# 

****---
category: Reference
type: ecosystem
ecosystem: http4k Connect
title: Slack
description: Feature overview of the http4k Connect Slack modules
---

```kotlin
dependencies {
    
    implementation(platform("org.http4k:http4k-bom:6.45.1.0"))

    implementation("org.http4k:http4k-connect-slack")
}
```

The Slack connector provides the following Actions:

- Send Channel Message

### Example usage





```kotlin
package content.ecosystem.connect.reference.slack

import org.http4k.connect.slack.Http
import org.http4k.connect.slack.Slack
import org.http4k.connect.slack.SlackWebhook
import org.http4k.connect.slack.chatPostMessage
import org.http4k.connect.slack.model.ChannelId
import org.http4k.connect.slack.model.SlackMessage
import org.http4k.connect.slack.model.SlackToken
import org.http4k.connect.slack.webhookPostMessage
import org.http4k.core.Uri

val message = SlackMessage("message", channel = ChannelId.of("channel"))
val slack = Slack.Http({ SlackToken.of("my slack token") })
val postResult = slack.chatPostMessage(message)

val webhooks = SlackWebhook.Http(Uri.of("https://hooks.slack.com/services/some/webhook/path"))
val webhookResult = webhooks.webhookPostMessage(message)

```



### Default Fake port: 23660

To start:





```kotlin
package content.ecosystem.connect.reference.slack

import org.http4k.chaos.start
import org.http4k.connect.slack.FakeSlack

val fakeSlack = FakeSlack().start()

```



