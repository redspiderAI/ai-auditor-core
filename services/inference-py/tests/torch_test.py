"""简单的 PyTorch 运行测试脚本
运行方式（在 services/inference-py 下）:

Windows PowerShell:
```powershell
uv run ./tests/torch_test.py -- --device cuda:0
# 或使用环境变量:
# set TORCH_DEVICE=cuda:0; uv run ./tests/torch_test.py
```
脚本行为：优先使用命令行参数 `--device`，其次读取环境变量 `TORCH_DEVICE`，最后在无指定时自动选择 GPU（若可用）或回退到 CPU。
"""
import sys
import os
import argparse

try:
    import torch
except Exception as e:
    print("ERROR_IMPORT_TORCH:", e)
    sys.exit(2)

parser = argparse.ArgumentParser(description="Simple PyTorch device test")
parser.add_argument("--device", type=str, default="cuda:0", help="Device to use, e.g. 'cpu' or 'cuda:0'")
args, _ = parser.parse_known_args()

# 决定 device：优先命令行，其次环境变量 TORCH_DEVICE，最后自动选择
env_device = os.environ.get("TORCH_DEVICE")
if args.device:
    device_str = args.device
elif env_device:
    device_str = env_device
else:
    device_str = "cuda" if torch.cuda.is_available() else "cpu"

try:
    device = torch.device(device_str)
except Exception:
    print(f"无法解析设备 '{device_str}'，回退到 cpu")
    device = torch.device("cpu")

print("torch version:", torch.__version__)
print("chosen device:", device)
print("cuda available:", torch.cuda.is_available())
if torch.cuda.is_available():
    try:
        print("cuda device count:", torch.cuda.device_count())
        print("current cuda device index:", torch.cuda.current_device())
        print("current cuda device name:", torch.cuda.get_device_name(torch.cuda.current_device()))
    except Exception as e:
        print("查询 CUDA 设备信息失败:", e)

# 简单张量操作并放到选定设备
x = torch.tensor([1.0, 2.0, 3.0], device=device)
print("tensor:", x)
print("device of tensor:", x.device)

try:
    # 一个小的张量计算验证
    y = x * 2.0
    print("computation result:", y)
except Exception as e:
    print("在设备上运行张量计算失败:", e)
    sys.exit(3)
