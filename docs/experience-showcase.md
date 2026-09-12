# Enterprise Experience Showcase

## Similar distributed system

In a large-scale gaming platform, I worked on decomposing a monolithic backend into independently deployable services around authentication, user/KYC, device limits, referrals and leaderboards. Kafka was used to decouple asynchronous workflows and reduce synchronous dependencies.

## Performance optimization

I focused on reducing critical-path latency and improving reliability through service decomposition, asynchronous processing and targeted data-access improvements. In one device-limit flow, the resulting design reduced referral-abuse/fraud by roughly 30% and improved onboarding/issue turnaround metrics.

## Production incident

A useful interview discussion is a high-severity production issue involving downstream latency or event-processing backlog: establish impact, stabilize first, isolate the failing dependency, reduce blast radius, restore service, then add monitoring, tests and architectural safeguards so the failure mode is less likely to recur.

## Tradeoff

The central tradeoff in this assessment is consistency versus throughput/decoupling. PostgreSQL provides durable source-of-truth writes while Kafka and Elasticsearch provide asynchronous indexing. The benefit is independent scaling and isolation of indexing load; the cost is a short period during which a successfully written document may not yet be searchable. A transactional outbox is the production answer to the DB/Kafka dual-write reliability gap.
