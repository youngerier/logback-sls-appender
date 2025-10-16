# 本地开发调试指南

## 问题说明

在本地开发时，如果直接运行 `mvn install` 或 `mvn package`，可能会遇到以下GPG签名错误：

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin:3.1.0:sign (sign-artifacts) on project logback-sls-appender: Could not determine gpg version -> [Help 1]
```

这是因为项目配置了GPG签名插件用于发布到Maven中央仓库，但本地开发环境通常没有配置GPG。

## 解决方案

### 方案1：使用本地开发Profile（推荐）

项目已经配置了 `local-dev` profile，默认激活并跳过GPG签名：

```bash
# 清理并编译
mvn clean compile

# 运行测试
mvn test

# 本地安装（跳过GPG签名）
mvn clean install

# 打包（跳过GPG签名）
mvn clean package
```

### 方案2：显式指定Profile

如果需要明确指定profile：

```bash
# 使用本地开发profile
mvn clean install -P local-dev

# 或者跳过GPG插件
mvn clean install -Dgpg.skip=true
```

### 方案3：仅编译和测试

如果只需要编译和测试，不需要安装到本地仓库：

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 编译并运行测试
mvn clean test
```

## 常用开发命令

### 基础命令

```bash
# 清理项目
mvn clean

# 编译源代码
mvn compile

# 编译测试代码
mvn test-compile

# 运行测试
mvn test

# 打包JAR文件
mvn package

# 安装到本地Maven仓库
mvn install
```

### 开发调试命令

```bash
# 查看依赖树
mvn dependency:tree

# 查看有效POM配置
mvn help:effective-pom

# 查看激活的profiles
mvn help:active-profiles

# 跳过测试打包
mvn package -DskipTests

# 详细输出模式
mvn clean install -X

# 离线模式（使用本地仓库）
mvn clean install -o
```

### IDE集成

#### IntelliJ IDEA
1. 导入项目时选择"Import project from external model" → "Maven"
2. 在Maven工具窗口中，确保 `local-dev` profile被激活
3. 运行配置中使用 `mvn clean install -P local-dev`

#### Eclipse
1. 导入项目：File → Import → Existing Maven Projects
2. 右键项目 → Properties → Maven → Active Maven Profiles
3. 添加 `local-dev` 到激活的profiles列表

#### VS Code
1. 安装 "Extension Pack for Java"
2. 打开项目文件夹
3. 使用集成终端运行Maven命令

## 发布版本

当需要发布到Maven中央仓库时，使用release profile：

```bash
# 发布版本（需要GPG配置）
mvn clean deploy -P release

# 或者设置系统属性
mvn clean deploy -DperformRelease=true
```

## 故障排除

### 问题1：GPG签名失败
**解决方案**：确保使用 `local-dev` profile 或添加 `-Dgpg.skip=true` 参数

### 问题2：依赖下载失败
**解决方案**：
```bash
# 强制更新依赖
mvn clean install -U

# 清理本地仓库缓存
mvn dependency:purge-local-repository
```

### 问题3：编译错误
**解决方案**：
```bash
# 检查Java版本（需要Java 17+）
java -version

# 设置JAVA_HOME环境变量
export JAVA_HOME=/path/to/java17

# Windows
set JAVA_HOME=C:\Program Files\Java\jdk-17
```

### 问题4：测试失败
**解决方案**：
```bash
# 跳过测试
mvn clean install -DskipTests

# 只运行特定测试
mvn test -Dtest=SlsGzipAppenderTest

# 详细测试输出
mvn test -Dsurefire.printSummary=true
```

## 项目结构

```
logback-sls-appender/
├── src/
│   ├── main/java/
│   │   └── io/github/youngerier/logback/sls/appender/
│   │       ├── SlsGzipAppender.java          # GZIP版本同步Appender
│   │       ├── AsyncSlsGzipAppender.java     # GZIP版本异步Appender
│   │       ├── SlsProtobufAppender.java      # Protobuf版本同步Appender
│   │       └── AsyncSlsProtobufAppender.java # Protobuf版本异步Appender
│   └── test/java/
│       └── io/github/youngerier/logback/sls/appender/
│           └── SlsGzipAppenderTest.java      # 测试类
├── pom.xml                                   # Maven配置文件
├── README.md                                 # 项目说明
├── README-GZIP.md                           # GZIP版本说明
└── LOCAL-DEVELOPMENT.md                     # 本文档
```

## 开发建议

1. **使用GZIP版本**：推荐使用 `SlsGzipAppender` 和 `AsyncSlsGzipAppender`，避免protobuf依赖冲突
2. **异步优先**：生产环境建议使用 `AsyncSlsGzipAppender` 以获得更好的性能
3. **本地测试**：开发时使用 `mvn test` 验证功能
4. **代码风格**：遵循项目现有的代码风格和命名规范
5. **文档更新**：修改功能时同步更新相关文档

## 联系方式

如有问题，请提交Issue到项目仓库：
https://github.com/youngerier/logback-sls-appender/issues