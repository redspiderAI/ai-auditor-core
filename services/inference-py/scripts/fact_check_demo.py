#!/usr/bin/env python3
"""示例脚本：
1) 从内置多行文本中提取每条参考文献为数组元素
2) 尝试连接 Milvus 并对每条参考用 embedding 检索相似文档（可降级）
3) 使用 DashScope (Qwen) 对比用户引用与检索到的真实信息，输出结构化 JSON

配置：将 Qwen API key 写入 services/inference-py/.env，键名示例 `DASHSCOPE_API_KEY`
输出：scripts/fact_check_output.json
"""

from __future__ import annotations

import json
import os
import re
import logging
import time
import socket
import concurrent.futures
from pathlib import Path
from typing import List, Dict, Any

try:
    from dotenv import load_dotenv
except Exception:
    load_dotenv = None

try:
    from pymilvus import connections, Collection
    MILVUS_PY_AVAILABLE = True
except Exception:
    MILVUS_PY_AVAILABLE = False

try:
    # 尝试导入项目内置的 Milvus Lite 启动器
    from src.fact_checking.milvus_lite import start_milvus_lite, ensure_collection
    HAVE_MILVUS_LITE = True
except Exception:
    HAVE_MILVUS_LITE = False

try:
    from sentence_transformers import SentenceTransformer
    ST_AVAILABLE = True
except Exception:
    ST_AVAILABLE = False

try:
    import dashscope
    DASHSCOPE_AVAILABLE = True
except Exception:
    DASHSCOPE_AVAILABLE = False

# 运行时缓存：如果首次调用 DashScope 返回空或失败，后续调用将被禁用以避免重复浪费时间
_DASHSCOPE_USABLE: bool | None = None

# Respect MILVUS_LITE_DISABLED from environment
_MILVUS_LITE_DISABLED = os.environ.get("MILVUS_LITE_DISABLED", "0") in ("1", "true", "True")


HERE = Path(__file__).resolve().parent
ENV_PATH = HERE.parent / ".env"
if load_dotenv and ENV_PATH.exists():
    load_dotenv(ENV_PATH)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

REFERENCES_TEXT = r'''
[1]董蔼莹. 基于VR技术的园区全景漫游系统设计与实现[D]. 广东:华南农业大学,2019.
[2]刘乾坤. 基于多张全景图的虚拟视点合成系统[D]. 天津:天津大学,2017.
[3]秦国防. 基于虚拟现实的数字三维全景技术的研究与实现[D]. 四川:电子科技大学,2011. DOI:10.7666/d.D813218.
[4]刘雨航,金一,竺长安. 基于SIFT特征提取和最佳缝合线的图像拼接技术[J]. 机械与电子,2017,35(6):18-21,25. DOI:10.3969/j.issn.1001-2257.2017.06.004.
[5]HU CHUILI. Research on Application of Virtual Reality Technology Based on Data Visualization in Visualization of Engineering Design[C]. //2019 4th International Industrial Informatics and Computer Engineering Conference (IIICEC 2019)2019年第四届国际工业信息学和计算机工程会议(IIICEC 2019)论文集. 2019:312-317.
[6]Application of Virtual Reality Technology in Aerospace[J]. 中国航天（英文版）,2017,18(3):43-47. DOI:10.3969/j.issn.1671-0940.2017.03.006.
[7]YANG LING. Feature Enhancement Strategy for Computer Three-Dimensional Bionic Images Based on Virtual Reality Technology[C]. //2019年第二届智能系统研究与机电工程国际会议(ISRME 2019) 2019 2nd International Conference on Intelligent Systems Research and Mechatronics Engineering论文集. 2019:494-497.
[8]WANG BIN, ZHAO QING, ZHAO PENG FEI. Application of Virtual Reality Technology in Laser Processing Training[C]. //The 12th International Conference on Modern Industrial Training(第十二届现代工业培训国际学术会议)论文集. 2019:77-79.
[9]WENLI HUANG. The Application of Virtual Reality Technology in Industrial Design[C]. //The 8th International Conference on Mechatronics, Computer and Education Informationization(第八届机电一体化、计算机与教育信息化国际会议)(MCEI2018) 论文集. 2018:324-327.
[10]XIANWEN XIU, HEPING WANG, JIAYING WANG, et al. Implementation of Multi-layer Switching and Loading Technology for 3D Panorama Platform[C]. //2018 3rd International Conference on Materials Science, Machinery and Energy Engineering (MSMEE 2018) 2018第三届材料科学、机械与能源工程国际会议(MSMEE 2018) 论文集. 2018:253-257.
[11]JIANPING WU, JIEHUA WANG, QIHUI GONG, et al. Panorama Photographs Based 3D Virtual Street Scene Construction and Integration with GIS[C]. //2011 International Conference on Opto-Electronics Engineering and Information Science(2011光电电子工程与信息科学国际会议 ICOEIS 2011)论文集. 2011:603-607.
[12]Virtual Reality: A State-of-the-Art Survey[J]. 国际自动化与计算杂志（英文版）,2009,6(4):319-325. DOI:10.1007/s11633-009-0319-9.
[13]LU YAN. When Virtual Faces Reality[J]. 北京周报（英文版）,2022,65(5):40-41. DOI:10.3969/j.issn.1000-9140.2022.05.015.
[14]SHUGUO GAO, WEI LIU, JIN PAN, et al. Research and Application of 3D panoramic technology on equipment visualization[C]. //2012 International Conference on Computer Science and Electronic Engineering(2012 IEEE计算机科学与电子工程国际会议 ICCSEE 2012)论文集. 2012:562-565.
[15]ENJI SUN, CUIPING LI, CONG SHI. Hazards Detection System Based on 3D Panorama for Tailings Dam in Mining[C]. //The 29th SOMP Annual Meeting and Conference on Mines of the Future(SOMP2018)第29届国际矿业教授学会学术年会暨未来矿山国际论坛论文集. 2018:120-126.
[16]YANG LING, CHENG YONG, CHENG YUN. The Key Technology of Virtual Reality System Based on Panoramic View[C]. //2011 3nd International Conference on Mechanical and Electronics Engineering(2011年第三届机械与电子工程国际会议 ICMEE2011)论文集. 2011:3123-3127.
[17] QIAN CHEN, DAI LUO. Creation and Research of VR Panorama Video in Jiu Feng Park[C]. //2019 2nd International Conference on Mechanical Engineering, Industrial Materials and Industrial Electronics (MEIMIE 2019)2019年第二届机械工程、工业材料和工业电子国际会议(Meimie 2019)论文集. 2019:253-258.
[18]Software Testing Applications Based on a Virtual Reality System[J]. 中国电子科技（英文版）,2007,5(2):120-124.
[19]武刚,余武. 虚拟校园三维全景漫游系统探究与实现[J]. 现代教育技术,2013,23(5):122-126. DOI:10.3969/j.issn.1009-8097.2013.05.025.
[20]冯新玲. 三维虚拟校园交互漫游系统的设计与实现[D]. 江苏:南京理工大学,2018.
[21]赵强.面向虚拟浏览的全景图像处理[D]. 天津:天津大学,2015. DOI:10.7666/d.D01157413.
[22]黄显兵. 基于全景技术的景观在线漫游的设计与实现[D]. 上海:上海交通大学,2012.
[23]杨笛航. 基于多全景相机拼接的虚拟现实和实景交互系统[D]. 浙江:浙江大学,2017.
[24]彭凤婷. 全景视频图像融合与拼接算法研究[D]. 四川:电子科技大学,2017.
[25]张少坤. 全景图像拼接关键技术研究[D]. 陕西:西安建筑科技大学,2018. DOI:10.7666/d.D01575242.
[26]郑昊. 基于改进SIFT算法的图像匹配研究[D]. 安徽:安徽理工大学,2020.
[27]卢奇. 基于改进SIFT的图像拼接算法研究及其评价[D]. 上海:上海海洋大学,2019.
[28]沈鹏. 基于SIFT特征图像拼接的全景显示技术研究[D]. 四川:电子科技大学,2018.
[29]勾会杰,王树根,王治邺,等. 一种引入 RANSAC 算法的匹配点云规则化方法[J]. 测绘与空间地理信息,2015(8):34-36,40. DOI:10.3969/j.issn.1672-5867.2015.08.012.
韦杨. 基于Unity的虚拟校园系统的设计与实现[D]. 广西:广西大学,2020.
'''


def extract_references(text: str) -> List[str]:
    """将多行参考文献文本切分为条目列表，支持两种常见格式：以 [n] 开头或每行一个条目。"""
    entries: List[str] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        m = re.match(r'^\s*\[?(\d+)\]?\s*(.*)$', line)
        if m:
            entries.append(m.group(2).strip())
        else:
            # 非标准行：作为上一条的延续
            if entries:
                entries[-1] = entries[-1] + ' ' + line
            else:
                entries.append(line)

    return entries


def simple_extract_info(reference: str) -> Dict[str, str]:
    """简易的参考文献信息抽取（title, year, authors, doi）用作降级方案"""
    info: Dict[str, str] = {"title": "", "year": "", "authors": "", "doi": ""}
    ref = reference
    # DOI
    doi_m = re.search(r'(10\.\d{4,9}/[^\s,;]+)', ref)
    if doi_m:
        info["doi"] = doi_m.group(1)

    # 年份
    year_m = re.search(r'(19|20)\d{2}', ref)
    if year_m:
        info["year"] = year_m.group(0)

    # 作者（简单取开头连续的英文大写单词或中文姓名列表）
    auth_m = re.match(r'^([\u4e00-\u9fa5A-Za-z ,\.]+)\.', ref)
    if auth_m:
        info["authors"] = auth_m.group(1).strip()

    # 标题：尝试抽取引号、书名号或第一个句号之后的短片段
    title_m = re.search(r'“([^”]+)”|"([^"]+)"|《([^》]+)》', ref)
    if title_m:
        info["title"] = title_m.group(1) or title_m.group(2) or title_m.group(3)
    else:
        # 尝试取第一个句号之后到下一标点
        parts = re.split(r'[\.。:：,]', ref)
        if len(parts) > 1:
            info["title"] = parts[1].strip()

    return info


def connect_milvus_if_possible() -> Dict[str, Any]:
    cfg = {}
    host = os.environ.get("MILVUS_HOST", "127.0.0.1")
    port = int(os.environ.get("MILVUS_PORT", "19530"))
    cfg["host"] = host
    cfg["port"] = port

    if not MILVUS_PY_AVAILABLE:
        logging.warning("pymilvus not available; skipping Milvus step")
        cfg["connected"] = False
        return cfg

    def _try_connect() -> bool:
        try:
            connections.connect(host=host, port=port)
            logging.info("Connected to Milvus at %s:%s", host, port)
            return True
        except Exception as e:
            logging.warning("Failed to connect to Milvus: %s", e)
            return False

    if _try_connect():
        cfg["connected"] = True
        return cfg

    cfg["connected"] = False

    # 如果无法连接，尝试启动内置 Milvus Lite（如果可用）并等待端口就绪
    if _MILVUS_LITE_DISABLED:
        logging.info("MILVUS_LITE_DISABLED is set; skipping embedded Milvus start")
        return cfg
    started = False
    if HAVE_MILVUS_LITE:
        try:
            logging.info("Attempting to start embedded Milvus Lite via module...")
            uri = start_milvus_lite()
            try:
                ensure_collection(uri)
            except Exception:
                pass
            started = True
        except Exception as e3:
            logging.warning("Failed to start embedded Milvus Lite via module: %s", e3)

    # 如果没有模块方式可用，尝试用子进程启动脚本（非阻塞）
    if not started:
        start_script = HERE / "start_milvus_lite.py"
        if start_script.exists():
            try:
                import subprocess
                logging.info("Spawning background Milvus Lite script: %s", start_script)
                subprocess.Popen(["python", str(start_script)], cwd=str(HERE), stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                started = True
            except Exception as e4:
                logging.warning("Failed to spawn Milvus Lite script: %s", e4)

    if started:
        # 等待端口就绪再尝试连接
        def wait_for_port(hostname: str, portnum: int, timeout_s: int = 30) -> bool:
            deadline = time.time() + timeout_s
            while time.time() < deadline:
                try:
                    with socket.create_connection((hostname, portnum), timeout=2):
                        return True
                except Exception:
                    time.sleep(1)
            return False

        wait_timeout = int(os.environ.get("MILVUS_START_WAIT_S", "30"))
        logging.info("Waiting up to %s seconds for Milvus port %s:%s to become available...", wait_timeout, host, port)
        if wait_for_port(host, port, timeout_s=wait_timeout):
            try:
                if _try_connect():
                    cfg["connected"] = True
                    logging.info("Connected to Milvus after starting Milvus Lite at %s:%s", host, port)
                    return cfg
            except Exception as e2:
                logging.warning("Still failed to connect to Milvus after starting Milvus Lite: %s", e2)
        else:
            logging.warning("Milvus port not reachable after waiting %s seconds", wait_timeout)
        return cfg

    return cfg


def search_milvus_for_queries(queries: List[str], collection_name: str = "academic_papers") -> List[List[Dict[str, Any]]]:
    results = []
    if not MILVUS_PY_AVAILABLE:
        return [[] for _ in queries]

    try:
        from pymilvus import utility
        if not utility.has_collection(collection_name):
            logging.info("Collection %s not found in Milvus; skipping search", collection_name)
            return [[] for _ in queries]
    except Exception as e:
        logging.warning("Milvus utility check failed: %s", e)
        return [[] for _ in queries]

    # build embeddings
    embedder = None
    if ST_AVAILABLE:
        try:
            embedder = SentenceTransformer("all-MiniLM-L6-v2")
        except Exception as e:
            logging.warning("Failed to load SentenceTransformer: %s", e)

    for q in queries:
        qres: List[Dict[str, Any]] = []
        if embedder is None:
            results.append(qres)
            continue

        try:
            vec = embedder.encode([q], normalize_embeddings=True)[0].tolist()
        except Exception as e:
            logging.warning("Embedding failed for query: %s", e)
            results.append(qres)
            continue

        try:
            coll = Collection(collection_name)
            try:
                coll.load()
            except Exception:
                pass
            search_params = {"metric_type": "IP", "params": {"nprobe": 10}}
            search_res = coll.search(data=[vec], anns_field="embedding", param=search_params, limit=5, output_fields=["title", "authors", "year", "journal", "reference"])
            for hit in (search_res[0] if search_res else []):
                ent = getattr(hit, "entity", None) or {}
                qres.append({
                    "title": ent.get("title"),
                    "authors": ent.get("authors"),
                    "year": ent.get("year"),
                    "journal": ent.get("journal"),
                    "reference": ent.get("reference"),
                    "score": float(hit.score) if hasattr(hit, "score") else None,
                })
        except Exception as e:
            logging.warning("Milvus search failed for query: %s", e)

        results.append(qres)

    return results


def call_qwen_for_comparison(reference: str, retrieved_docs: List[Dict[str, Any]]) -> Dict[str, Any]:
    """调用 Qwen（通过 dashscope）或降级到本地规则比对，返回结构化结果"""
    api_key = os.environ.get("DASHSCOPE_API_KEY")
    llm_used = False
    llm_text = None
    llm_error = None
    global _DASHSCOPE_USABLE
    if _DASHSCOPE_USABLE is False:
        llm_error = "dashscope_disabled_by_prev_failure"
    elif DASHSCOPE_AVAILABLE and api_key:
        try:
            dashscope.api_key = api_key
            prompt = build_prompt(reference, retrieved_docs)
            # 使用线程池为 LLM 调用设置超时，并且允许最多一次重试
            attempts = 0
            max_attempts = 2
            while attempts < max_attempts:
                attempts += 1
                try:
                    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as exe:
                        fut = exe.submit(lambda: dashscope.Generation.call(model="qwen-long", prompt=prompt, top_p=0.8, temperature=0.3, max_tokens=800))
                        resp = fut.result(timeout=int(os.environ.get("DASH_CALL_TIMEOUT_S", "20")))

                    status = getattr(resp, "status_code", None)
                    out = getattr(resp, "output", None)
                    text = getattr(out, "text", None) if out is not None else None
                    if status == 200 and text:
                        llm_used = True
                        llm_text = text
                        _DASHSCOPE_USABLE = True
                        break
                    else:
                        llm_error = "empty_text" if status == 200 else f"status_{status}"
                        logging.warning("Dashscope attempt %s returned: %s", attempts, llm_error)
                        time.sleep(1)
                except concurrent.futures.TimeoutError:
                    llm_error = "timeout"
                    logging.warning("Dashscope attempt %s timeout", attempts)
                    time.sleep(1)
                except Exception as e:
                    llm_error = str(e)
                    logging.warning("Dashscope attempt %s failed: %s", attempts, llm_error)

            if not llm_used:
                # 标记后续调用跳过
                _DASHSCOPE_USABLE = False
        except Exception as e:
            llm_error = str(e)
            _DASHSCOPE_USABLE = False

    # 如果 LLM 可用且返回文本，则解析并返回；否则降级为本地比对并记录原因
    if llm_used and llm_text:
        return {"llm_used": True, "llm_raw": llm_text}

    if llm_error:
        logging.warning("Dashscope returned empty text or error: %s; falling back to local analysis", llm_error)

    # 降级比对：基于正则抽取字段并做简单匹配
    info_user = simple_extract_info(reference)
    best = retrieved_docs[0] if retrieved_docs else None
    info_ret = simple_extract_info(best.get("reference", "")) if best and best.get("reference") else (best or {})
    score = 0.0
    if info_user.get("year") and info_ret.get("year") and info_user.get("year") == info_ret.get("year"):
        score += 0.5
    if info_user.get("title") and info_ret.get("title") and info_user.get("title")[:10] == info_ret.get("title")[:10]:
        score += 0.4
    if info_user.get("doi") and info_ret.get("doi") and info_user.get("doi") == info_ret.get("doi"):
        score = 1.0

    return {"llm_used": False, "llm_error": llm_error, "is_valid_estimate": score >= 0.8, "estimated_score": score, "user_info": info_user, "retrieved_top": info_ret}


def build_prompt(reference: str, retrieved_docs: List[Dict[str, Any]]) -> str:
    retrieved_text = "\n".join([f"- {d.get('title','Unknown')} ({d.get('year','?')}) by {d.get('authors','?')}" for d in (retrieved_docs or [])[:5]]) or "未检索到相关文献"
    prompt = f"""你是一位学术文献审核专家。
请对比用户提供的参考文献：\n{reference}\n与检索到的文献信息：\n{retrieved_text}\n
请输出 JSON: {{"is_valid": true/false, "confidence_score":0.0-1.0, "issues":[{{"type":"YEAR_MISMATCH|TITLE_MISMATCH|NOT_FOUND","description":"...","severity":"HIGH|MEDIUM|LOW"}}], "explanation":"..."}}"""
    return prompt


def main():
    entries = extract_references(REFERENCES_TEXT)
    logging.info("Extracted %d references", len(entries))

    milvus_cfg = connect_milvus_if_possible()
    retrieved = []
    if milvus_cfg.get("connected"):
        retrieved = search_milvus_for_queries(entries)
    else:
        logging.info("Milvus not connected or unavailable; skipping retrieval")
        retrieved = [[] for _ in entries]

    results = []
    for ref, docs in zip(entries, retrieved):
        t0 = time.time()
        analysis = call_qwen_for_comparison(ref, docs)
        duration_ms = int((time.time() - t0) * 1000)
        # 记录降级原因和计时
        downgrade_reasons = []
        if not docs:
            downgrade_reasons.append("no_milvus_results")
        if not analysis.get("llm_used"):
            downgrade_reasons.append("llm_missing_or_failed")
        if downgrade_reasons:
            analysis.setdefault("downgrade_reason", []).extend(downgrade_reasons)
        analysis["duration_ms"] = duration_ms
        results.append({"reference": ref, "retrieved": docs, "analysis": analysis})

    out_path = HERE / "fact_check_output.json"
    with out_path.open("w", encoding="utf-8") as f:
        json.dump({"results": results}, f, ensure_ascii=False, indent=2)

    logging.info("Wrote output to %s", out_path)


if __name__ == "__main__":
    main()
