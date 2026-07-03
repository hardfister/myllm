myllm/
├── .gradle/                        # Gradle 自动生成的缓存目录
├── gradle/                         # Gradle 包装器目录
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── myllm/
│   │   │               ├── config/         # ⚙️ 配置层：动态初始化 ChatModel、VectorStore
│   │   │               │   └── AiConfig.java
│   │   │               ├── controller/     # 🌐 控制层：提供前端调用的 API 接口
│   │   │               │   ├── ChatController.java       # 聊天接口
│   │   │               │   ├── ModelController.java      # 模型切换接口
│   │   │               │   └── KnowledgeController.java  # 知识库上传接口
│   │   │               ├── service/        # 🧠 业务逻辑层：处理 RAG、Prompt 拼接、调用大模型
│   │   │               │   ├── ChatService.java
│   │   │               │   └── KnowledgeService.java
│   │   │               ├── model/          # 📦 实体层：存放各种请求和返回的参数对象 (DTO/Entity)
│   │   │               │   ├── dto/
│   │   │               │   │   ├── ChatRequest.java      # 接收用户输入的 prompt、人设等
│   │   │               │   │   └── ModelSetting.java     # 接收前端传来的 API Key 和 Base URL
│   │   │               └── MyllmApplication.java         # 🚀 整个项目的启动类（自带）
│   │   └── resources/
│   │       ├── static/             # 🎨 前端静态资源：放你的 UI 页面 (index.html, js, css)
│   │       │   └── index.html      # 之后的傻瓜式调试 UI 就可以放在这里
│   │       ├── templates/          # 后端模板页面（如果用 Thymeleaf 的话，不用可留空）
│   │       └── application.yml     # 📝 全局配置文件（或者是 application.properties）
│   └── test/                       # 测试目录（暂时不用管）
├── build.gradle                    # 🛠️ Gradle 的依赖管理文件（相当于 Maven 的 pom.xml）
└── settings.gradle                 # Gradle 项目设置