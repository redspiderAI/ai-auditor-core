import requests
import time
import json

# 等待服务启动
print("等待服务启动...")
time.sleep(5)

# 测试网关健康检查
try:
    response = requests.get("http://localhost:8080/health")
    print(f"网关健康检查: {response.status_code} - {response.json()}")
except Exception as e:
    print(f"网关健康检查失败: {e}")

# 上传测试文档
try:
    with open('test_document.txt', 'rb') as f:
        files = {'file': ('test_document.txt', f, 'text/plain')}
        response = requests.post('http://localhost:8080/api/v1/audit', files=files)
    
    print(f"上传响应: {response.status_code}")
    if response.status_code == 202:
        task_data = response.json()
        task_id = task_data['task_id']
        print(f"任务ID: {task_id}")
        
        # 查询任务状态
        status_url = f"http://localhost:8080/api/v1/tasks/{task_id}"
        for i in range(20):  # 最多等待100秒
            time.sleep(5)
            status_response = requests.get(status_url)
            if status_response.status_code == 200:
                status_data = status_response.json()
                print(f"任务状态: {status_data['status']}, 进度: {status_data['progress']}")
                
                if status_data['status'] == 'completed':
                    # 获取报告
                    report_url = f"http://localhost:8080/api/v1/report/{task_id}"
                    report_response = requests.get(report_url)
                    if report_response.status_code == 200:
                        report_data = report_response.json()
                        print("报告数据:")
                        print(json.dumps(report_data, indent=2, ensure_ascii=False))
                    else:
                        print(f"获取报告失败: {report_response.status_code}")
                    break
                elif status_data['status'] == 'error':
                    print(f"任务出错: {status_data.get('error_msg', 'Unknown error')}")
                    break
            else:
                print(f"查询状态失败: {status_response.status_code}")
    else:
        print(f"上传失败: {response.text}")
except Exception as e:
    print(f"测试过程中出现异常: {e}")