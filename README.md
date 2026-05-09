# MDT Vote Dependency

`mdt-vote-dependency` 是一个可复用的投票中间层插件。

它适合被其他插件、服务端接口或者外部 API 直接调用，用来发起统一的投票流程，而不是每个业务插件都各自重复实现一套投票逻辑。

## 已支持的投票动作

- `kick-player`
- `spectate-player`
- `redirect-player`
- `redirect-all`
- `custom-message`
- `world-operation`

## 已提供能力

- 公共 Java API：`VoteDependencyPlugin.getApi()`
- 单会话全服投票控制
- HUD 渲染联动 `mdt-uhd-renderer`
- 在线玩家快照投票
- 成功后自动执行动作
- 客户端投票命令 `/vote yes`、`/vote no`

## 服务端命令

- `vote-dep-status`
- `vote-dep-start <actionType> <target> <title...>`
- `vote-dep-cancel [reason...]`

## 配置目录

`config/mods/config/mdt-vote-dependency/vote-dependency.properties`

## 其他插件调用思路

通过反射获取：

- `com.mdt.vote.VoteDependencyPlugin`
- `getApi()`
- `startVote(VoteCreateRequest request)`

如果需要按 `comId` 指定玩家目标，建议同时安装 `mdt-jump-plugin`。
