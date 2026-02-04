# 手动测试脚本 - 用于测试分布式审查编排器模块

Write-Host "等待服务启动..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# 检查服务健康状态
Write-Host "检查服务健康状态..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri http://localhost:8080/health -Method Get
    Write-Host "服务状态: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "无法连接到服务，请确保服务已启动" -ForegroundColor Red
    exit 1
}

# 上传测试文档
Write-Host "上传测试文档..." -ForegroundColor Yellow
try {
    $fileBytes = [System.IO.File]::ReadAllBytes("sample_test_document.txt")
    $boundary = "----WebKitFormBoundary$(Get-Random)"
    
    $body = [System.IO.MemoryStream]::new()
    $writer = [System.IO.StreamWriter]::new($body)
    
    # 添加文件字段
    $writer.WriteLine("--$boundary")
    $writer.WriteLine("Content-Disposition: form-data; name=`"file`"; filename=`"sample_test_document.txt`"")
    $writer.WriteLine("Content-Type: application/octet-stream")
    $writer.WriteLine("")
    $writer.Flush()
    
    # 写入文件内容
    $body.Write($fileBytes, 0, $fileBytes.Length)
    
    # 结束边界
    $writer.WriteLine("")
    $writer.WriteLine("--$boundary--")
    $writer.Flush()
    
    $contentType = "multipart/form-data; boundary=$boundary"
    
    # 发送请求
    $wc = [System.Net.HttpWebRequest]::Create("http://localhost:8080/api/v1/upload")
    $wc.Method = "POST"
    $wc.ContentType = $contentType
    $wc.ContentLength = $body.Length
    
    $reqStream = $wc.GetRequestStream()
    $body.Position = 0
    $body.CopyTo($reqStream)
    $reqStream.Close()
    
    $response = $wc.GetResponse()
    $streamReader = [System.IO.StreamReader]::new($response.GetResponseStream())
    $result = $streamReader.ReadToEnd()
    
    Write-Host "上传响应: $result" -ForegroundColor Green
    
    # 解析任务ID
    $taskIdObj = $result | ConvertFrom-Json
    $taskId = $taskIdObj.task_id
    
    if ($taskId) {
        Write-Host "任务ID: $taskId" -ForegroundColor Green

        # 轮询任务状态直到完成
        Write-Host "等待任务完成..." -ForegroundColor Yellow
        $status = "Pending"
        $progress = 0

        while ($status -ne "Completed" -and $status -ne "Error") {
            Start-Sleep -Seconds 2

            $taskStatus = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/tasks/$taskId" -Method Get
            $status = $taskStatus.status
            $progress = $taskStatus.progress

            Write-Host "状态: $status, 进度: $progress%" -ForegroundColor Cyan
        }

        if ($status -eq "Completed") {
            Write-Host "任务完成!" -ForegroundColor Green

            # 获取审查报告
            Write-Host "获取审查报告..." -ForegroundColor Yellow
            $report = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/report/$taskId" -Method Get
            Write-Host "合规率: $($report.compliance_rate)%" -ForegroundColor Green
            Write-Host "问题总数: $($report.issue_summary.total_count)" -ForegroundColor Green

            # 显示前几个问题
            if ($report.issues.Count -gt 0) {
                Write-Host "前几个问题:" -ForegroundColor Yellow
                $report.issues | Select-Object -First 3 | ForEach-Object {
                    Write-Host "  - $($_.description) (严重性: $($_.severity))"
                }
            }
        } else {
            Write-Host "任务失败: $($taskStatus.error_msg)" -ForegroundColor Red
        }
    } else {
        Write-Host "无法获取任务ID" -ForegroundColor Red
    }
} catch {
    Write-Host "上传失败: $($_.Exception.Message)" -ForegroundColor Red
}