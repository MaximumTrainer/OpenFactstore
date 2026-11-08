# FactstoreNotificationDeadLetter

**Severity:** warn

## What fired

A notification (Slack, webhook, or in-app) failed all three delivery attempts and was dead-lettered: saved with status `FAILED`. The `channel` label says which kind. People relying on that rule, for example to hear about a blocked deployment gate, did not hear about it.

## First checks

1. In the UI, open **Notification rules**, then the rule's **Delivery history**. Failed deliveries are counted at the top and marked `FAILED`.
2. For Slack and webhooks: is the endpoint up, and has its URL or token changed? A `404` or `401` from the receiver usually means a rotated or deleted webhook.
3. If many rules fail at once, check outbound network access from the service (egress rules, proxy).

## Recovery

- Fix the receiver or update the rule's channel configuration, then use the rule's **Test** action to confirm delivery.
- Tell the rule's audience what they missed: each failed delivery's payload holds the event's title and message.
