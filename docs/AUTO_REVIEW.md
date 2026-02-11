# 自动代码审查配置

本项目配置了自动代码审查系统，用于在提交 Pull Request 时自动发现潜在的 bug 和改进建议。

## 功能特性

### 1. 自动触发
- 当创建新的 Pull Request 时自动运行
- 当 Pull Request 有新的提交时自动运行
- 针对 `main` 和 `develop` 分支的 PR

### 2. 代码质量检查

#### 构建和测试
- ✅ Maven 构建验证
- ✅ 单元测试执行
- ✅ 测试结果统计（测试数量、失败、错误）

#### 代码覆盖率
- 📊 使用 JaCoCo 生成代码覆盖率报告
- 📈 覆盖率低于 70% 时会提示改进建议

#### 静态代码分析

**SpotBugs**
- 🐛 检测潜在的 bug 和代码问题
- 使用最高检测强度 (Max effort)
- 检测阈值设为 Low（发现更多潜在问题）

**PMD**
- 📋 代码质量和最佳实践检查
- 检测代码重复、复杂度、命名等问题
- 使用 Java quickstart 规则集

**Checkstyle**
- 🎨 代码风格一致性检查
- 使用 Google Java Style Guide
- 确保代码格式统一

### 3. 自动评论

审查完成后，系统会在 PR 中自动添加评论，包含：
- 测试结果摘要
- 代码覆盖率
- SpotBugs 发现的潜在 bug 数量
- PMD 发现的代码违规数量
- Checkstyle 发现的风格问题数量
- 改进建议

评论格式示例：

```markdown
## Code Review Summary

### ✅ Test Results
- Total tests: 15
- Failures: 0
- Errors: 0

### 📊 Code Coverage
- Coverage: 75%

### 🐛 SpotBugs Analysis
- Potential bugs found: 0

### 📋 PMD Analysis
- Code violations found: 3

### 🎨 Checkstyle Analysis
- Style issues found: 12

### 💡 Recommendations
- 💭 Consider addressing PMD violations to improve code quality

---
*Automated review by GitHub Actions*
```

## 本地运行

你也可以在本地运行这些检查工具：

### 运行所有测试
```bash
mvn clean test
```

### 生成代码覆盖率报告
```bash
mvn jacoco:prepare-agent test jacoco:report
# 报告位置: target/site/jacoco/index.html
```

### 运行 SpotBugs
```bash
mvn spotbugs:check
# 查看报告: target/spotbugsXml.xml
```

### 运行 PMD
```bash
mvn pmd:check
# 查看报告: target/pmd.xml
```

### 运行 Checkstyle
```bash
mvn checkstyle:check
# 查看报告: target/checkstyle-result.xml
```

### 运行所有检查
```bash
mvn clean compile test spotbugs:check pmd:check checkstyle:check
```

## 配置文件

### GitHub Actions 工作流
- 文件: `.github/workflows/pr-review.yml`
- 配置了完整的 CI/CD 流程，包括构建、测试和静态分析

### Maven 插件配置
- 文件: `pom.xml`
- 包含 SpotBugs、PMD、Checkstyle 和 JaCoCo 插件配置

## 自定义配置

### 调整 Checkstyle 规则
如需使用自定义的 Checkstyle 规则，可以：
1. 创建 `checkstyle.xml` 配置文件
2. 在 `pom.xml` 中修改 `configLocation` 指向你的配置文件

### 调整 PMD 规则
如需使用自定义的 PMD 规则，可以：
1. 创建 `pmd-ruleset.xml` 配置文件
2. 在 `pom.xml` 中修改 `rulesets` 配置

### 调整 SpotBugs 过滤器
如需排除某些 SpotBugs 检查，可以：
1. 创建 `spotbugs-exclude.xml` 过滤器文件
2. 在 `pom.xml` 中添加 `excludeFilterFile` 配置

## 最佳实践

1. **在提交 PR 前本地运行检查**
   - 确保代码质量符合标准
   - 避免 CI 流程失败

2. **及时修复高优先级问题**
   - SpotBugs 发现的 bug 应优先修复
   - 测试失败必须在合并前解决

3. **逐步改进代码质量**
   - PMD 和 Checkstyle 的警告可以逐步修复
   - 保持代码覆盖率在 70% 以上

4. **关注代码审查建议**
   - 自动审查发现的问题是真实的潜在隐患
   - 认真对待每一条改进建议

## 故障排除

### 工作流失败
- 检查 GitHub Actions 日志
- 确保所有依赖都能正常下载
- 验证 Java 版本兼容性（项目使用 Java 17）

### 插件错误
- 运行 `mvn clean` 清理构建缓存
- 检查 Maven 版本是否符合要求
- 查看详细的错误日志: `mvn -X <goal>`

## 参与贡献

如有改进建议或问题，欢迎提交 Issue 或 Pull Request。
