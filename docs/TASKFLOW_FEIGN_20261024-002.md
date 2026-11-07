# TaskFlow OpenFeign 验证报告

日期：2026-10-24（北京时间）
任务编号：20261024-002
项目：C:\Users\Administrator\Desktop\计算机学习\taskflow
关联会话：6ad8249b-78a0-83ec-a442-6737510096ac

## 结论

用户新写的 OpenFeign 业务代码在本次覆盖场景中通过验证，未发现需要修改的新业务逻辑问题。已保留 Spring Cloud BOM、Feign 依赖、@EnableFeignClients、UserClient 接口、TaskService 的 Feign 异常映射及超时配置。

本阶段只适配测试并更新报告；上一阶段已补充 GlobalExceptionHandler 对不可读请求体的统一 400 JSON 处理。

## 刚才的问题分别在哪里

1. HTTP 404 是用户不存在的预期结果：UserController 当前仅给用户 1 返回成功，用户 999 会返回 404。TaskService 的 FeignException.NotFound 分支与异常处理器正确保留状态和 JSON。
2. 本机 Windows PowerShell 5.1.26100.9444 的 ErrorDetails.Message 实测为空，但 Exception.Response.GetResponseStream() 中有完整正文。因此空白出现在客户端错误对象的显示路径，不能据此判断服务端返回空正文。
3. 原对话中的编译错误，是 Controller 方法名和 Service 不一致、客户端返回类型和 Service 接收类型不一致。开始本轮时源码已修正，未重现这两处原始错误。
4. 新迁移后的构建失败发生在旧测试：TaskApiTest 仍尝试 new UserClient(...)，而 UserClient 已改为 Feign 接口。实际 Maven 错误为“UserClient 是抽象的；无法实例化”。这是旧测试没有跟随迁移适配，不是 Feign 接口本身写错。
5. 额外发现并已修复的响应格式问题：错误 JSON、缺少请求体、字段类型错误此前未进入统一包装。现在保留 HTTP 400，返回 code/message/data 结构。
6. 最初原目录 clean verify 失败是正在运行的 JAR 被 Windows 锁定。后续完整构建在隔离源码副本完成，没有强行替换运行 JAR。

404 的实测正文：

```json
{"code":404,"message":"User not found","data":null}
```

## 当前 Feign 版本验证

| 验证 | 结果 |
| --- | --- |
| 项目原目录 mvnw.cmd -B test | 18 项全部通过：user 2 项、task 16 项 |
| 当前源码隔离副本 mvnw.cmd -B clean verify | 四模块全部成功，18 项通过，两个可执行 JAR 打包成功 |
| 新 JAR：task 18082 → user 18081 | 成功 200、用户不存在 404、错误 JSON 400，均带预期 JSON |
| 停止本轮临时 user 后调用临时 task | 实际返回 503 和 User Service is unreachable 正文 |
| 测试中的上游故障 | 500、空响应、异常业务码、缺失数据、非 JSON 均映射到 502 |
| 配置中的 readTimeout=3000 | 临时上游延迟 4500 毫秒，实际 Feign 调用返回 503 |
| 请求校验 | 无效用户 ID、缺少名称、空白名称及不可读请求体均返回 400 |

TaskApiTest 现在加载实际 Spring 应用和实际 Feign Bean，使用本机随机端口的临时 HTTP 服务提供受控上游响应，验证实际路径和解码、状态处理、读取超时。未伪造 Feign 返回对象来绕过客户端。

Spring Boot 3.5.16 与所配置 Spring Cloud 2025.0.3 在本项目成功启动并通过构建；解析到 Spring Cloud OpenFeign 4.3.3、Feign 13.6.1。本次应用未关闭 Spring Cloud 兼容性检查。

参考：[Spring Cloud OpenFeign 官方配置和超时说明](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html)。配置名 taskflow-user 与 @FeignClient 的 name 一致。

运行日志中有本机 hostname 无法确定的提示及测试 JVM class sharing 警告，均未阻止应用启动或使测试失败；本轮没有为这些提示改动系统配置。

## 修改与保留范围

本阶段修改：
- taskflow-task/src/test/java/com/taskflow/task/TaskApiTest.java：适配实际 Feign 客户端，保留原有场景并验证实际读取超时。
- docs/TASKFLOW_FEIGN_20261024-002.md：本报告。
- docs/TASKFLOW_DEBUG_20261024-001.md：标明后续验证已经完成，保留前阶段历史记录。

本阶段没有改写用户新写的业务代码，没有部署或推送。只启动并停止本轮创建的临时进程；最后未检测到 18081/18082 的监听。最终检查时，开始阶段的原有 PID 已退出，8081/8082 也未检测到监听；本轮没有停止那两个原有 PID，也没有自动重新启动它们。

隔离构建只用于验证，没有复制新 JAR 覆盖原项目旧 JAR。需要运行新版时，应自行使用新构建并启动，不要仅依赖旧运行实例来判断源码改动是否生效。

## Windows CMD 复现方式

在项目根目录运行测试：

```cmd
mvnw.cmd -B test
```

查看用户服务 404 正文：

```cmd
curl.exe -i http://localhost:8081/api/users/999
```

查看任务服务 404 正文（此命令已在 CMD 实测）：

```cmd
echo {"userId":999,"name":"demo"} | curl.exe -i -H "Content-Type: application/json" --data-binary @- "http://localhost:8082/api/tasks/validate-user"
```

## Windows PowerShell 5.1 读取错误正文

若继续使用 Windows PowerShell 5.1，可先取 ErrorDetails.Message，为空时读取响应流：

```powershell
try {
    Invoke-RestMethod -Uri "http://localhost:8082/api/tasks/validate-user" `
        -Method Post -ContentType "application/json" `
        -Body '{"userId":999,"name":"demo"}' -ErrorAction Stop
}
catch {
    $response = $_.Exception.Response
    $body = $_.ErrorDetails.Message
    if ($response) {
        Write-Host "HTTP 状态码:" ([int]$response.StatusCode)
        if ([string]::IsNullOrWhiteSpace($body)) {
            $stream = $response.GetResponseStream()
            if ($stream.CanSeek) { $stream.Position = 0 }
            $reader = New-Object System.IO.StreamReader($stream)
            try { $body = $reader.ReadToEnd() }
            finally { $reader.Dispose() }
        }
    }
    Write-Host "错误响应:" $body
}
```

该响应流读取方式已在本机 Windows PowerShell 5.1 实测。响应流可被消费，诊断时应读取一次并保留结果。


## 本地 Git 存档

前阶段 20261024-001：before 2fe082e，after 692cbad，修复统一 400 响应并验证原 RestClient 快照。

本阶段 20261024-002：before 408692a0d7fbdd7e422d0fe2c6cd8b74a961d2da，已包含用户最新 Feign 改动；after 记录测试适配、报告与本次验证结果，完整哈希附于交付副本末尾。

target、IDE 文件、HELP.md 等按原有 .gitignore 排除。Git 仅存档跟踪文件，不含忽略文件、运行进程、数据库及外部服务状态。未部署、未推送远程。
