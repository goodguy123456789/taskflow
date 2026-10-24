后续状态：已按用户要求继续验证 OpenFeign 新代码，18 项测试及完整打包通过。本文记录前阶段历史，当前结论见 TASKFLOW_FEIGN_20261024-002.md。

# TaskFlow 本地排查报告

日期：2026-10-24（北京时间）
任务编号：20261024-001
项目：C:\Users\Administrator\Desktop\计算机学习\taskflow
关联会话：6ad8249b-78a0-83ec-a442-6737510096ac

## 已核实的结论

1. 原对话中的两处编译不一致已在本轮开始前修正：TaskController 调用 validateUserAndTask；UserClient.getUser 返回 ApiResponse<UserInfo>，与 TaskService 一致。本轮没有改写这些已正确的逻辑。
2. UserController 对用户 1 返回 200，对用户 999 返回 404。当前仅用户 1 存在，这是示例接口的既定行为。
3. TaskService 捕获用户服务的 HTTP 404 并转换为 ResponseStatusException；GlobalExceptionHandler 返回对应状态和 ApiResponse JSON。RestClient 默认会对 4xx/5xx 抛出异常，因此 404 不会作为普通成功响应进入后续业务码检查。
4. 在原有 8081、8082 服务上直接请求，404 正文均为：
   `{"code":404,"message":"User not found","data":null}`
5. 本机 Windows PowerShell 5.1.26100.9444 实测：Invoke-RestMethod 抛出 System.Net.WebException，HTTP 状态为 404，ErrorDetails.Message 为空，但从 Exception.Response.GetResponseStream() 可以完整读取上述 JSON。因此这次“错误响应为空”是该错误对象未填充正文，不能据此判断服务端没有正文。没有为了改变 PowerShell 显示而把 HTTP 404 改成 200。

Spring 官方行为说明：[RestClient 错误处理](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)。

## 实际修复

GlobalExceptionHandler 原先只处理 ResponseStatusException。错误 JSON、缺少请求体或字段类型错误会抛出 HttpMessageNotReadableException，实际服务器返回 Spring 默认错误结构，破坏接口的统一响应格式。

新增该异常的专用处理，保留 HTTP 400，正文统一为：

```json
{"code":400,"message":"Invalid request body","data":null}
```

保留原有业务校验错误消息 Invalid task request。没有添加吞掉所有异常的兜底处理，也没有修改其他状态码的含义。

变更文件：
- taskflow-task/src/main/java/com/taskflow/task/exception/GlobalExceptionHandler.java
- taskflow-task/pom.xml、taskflow-user/pom.xml：仅添加 test 范围的 spring-boot-starter-test。
- taskflow-task/src/test/java/com/taskflow/task/TaskApiTest.java
- taskflow-user/src/test/java/com/taskflow/user/UserApiTest.java
- docs/TASKFLOW_DEBUG_20261024-001.md：本报告。

## 验证结果与范围

| 验证 | 结果 |
| --- | --- |
| 修复前回归测试 | 18 项中 3 项按预期失败，均为不可读请求体未返回统一 JSON |
| 项目原目录 mvnw.cmd -B test（并发迁移前） | 18 项全部通过：user 2 项，task 16 项 |
| 源码隔离副本 mvnw.cmd -B clean verify | 四模块全部成功，18 项全部通过，两个可执行 JAR 打包成功 |
| 新 JAR 临时实例的真实 HTTP 请求 | 200 成功、404 用户不存在、400 业务参数错误、400 错误 JSON 均符合预期 |
| Windows CMD 诊断命令 | 已实测返回 404 及完整 JSON |
| 清理 | 已停止本轮临时实例；原有 Java 进程 10948、34840 保留 |

测试覆盖真实 Controller、Service、UserClient 和异常处理器；用户服务边界通过 MockRestServiceServer 提供受控 HTTP 响应。502 覆盖上游 500、空响应、异常业务码、缺失数据和非 JSON；503 覆盖连接失败。这些故障分支由测试模拟，没有通过停止用户现有服务来验证。

原目录最初执行 clean verify 因正在运行的 taskflow-user JAR 被 Windows 锁定而失败，并非 Java 编译失败；因此改用隔离副本完整验证。最初 clean 已清理部分 target 内容，随后 test 重建了编译输出，原有运行 JAR 未被替换。根目录 packaging 为 pom，其遗留 src/test 模板不属于子模块测试套件，18 项指新增的两个子模块测试类。

现有服务没有重启，仍使用此前 JAR。此次修改已写入源码，并在隔离 JAR 中验证；下次使用修复需先停止相应旧进程，再重新打包启动。本轮未部署、未推送远程。

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

## 本地版本存档

before：2fe082e3f790a7ab8f2a43cedcdc9f9281ce4e40。仓库在指定 TaskFlow 根目录初始化，before 包含原有源码和配置；before/after 使用相同任务编号和需求摘要。

按原有 .gitignore 排除 target、IDE 配置与 HELP.md。没有强制跟踪忽略文件、密钥、依赖缓存或构建产物。Git 存档仅覆盖跟踪文件，不包含运行进程、数据库或外部服务状态。after 的提交哈希见本次完成回复及交付报告末尾。

## 最终核对发现的并发修改

完成上述测试和隔离构建后，最终 Git 检查发现项目同时出现了 OpenFeign 迁移：
- 根 pom.xml 新增 Spring Cloud BOM。
- taskflow-task/pom.xml 新增 OpenFeign 依赖。
- TaskApplication 开启 Feign。
- UserClient 从 RestClient 具体类改为 Feign 接口。
- TaskService 改为捕获 Feign 异常。
- application.yml 新增 Feign 超时和用户服务 URL 配置。

这些修改不是本轮所写，已保留在工作区，不纳入本轮 after。taskflow-task/pom.xml 的 after 仅存入已验证的测试依赖增量，工作区中的 Feign 依赖保留未提交。

因此，以上 18/18 和 clean verify 结论仅适用于本轮测试时的 RestClient 快照，不能用于证明当前混合工作区通过验证。当前新增 TaskApiTest 使用旧的 UserClient 构造方式，与后来改成接口的版本不兼容；OpenFeign 迁移的测试适配和重新验证尚未完成，已向用户确认后续范围。当前工作区并非干净状态是由于保留这些并发修改，不应将它们直接还原。

如需定位可重复验证的本轮快照，应使用本任务 after 提交；不要覆盖当前工作区。原有运行服务与新 Feign 源码之间也可能存在版本差异。
