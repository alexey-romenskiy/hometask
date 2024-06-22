# hometask

## How to use this code

### Requirements

Requires Apache Maven 3 and JDK 22.

### Running unit-tests

    mvn clean test

### Running integration tests

    mvn clean verify

## Design Choices

### Motivation

The following key points were taken into consideration:

* I was asked to "treat it as if it's a production piece of work"
  and how I would "approach building high-quality reliable software".
* Solution should be close to reality
* Coding like "writing code for you current company"
* Datastore runs in memory for the sake of simplicity

In my opinion, some of these points are mutually exclusive. For example, in reality I would never deploy to production
anything working without durability and consistency guarantees. In-memory datastore does not provide durability.
Solution without durability is not close to reality. The software design created with durability and
high-availability in mind may be completely different, and I cannot show my skills of implementing reliable
solutions (like I do it for my current company) by implementing totally different software design.

Therefore, I decided to use a design very close to what I use for the current company to allow durability,
high-availability and low-latency, but without the actual durability. This design enables us to (relatively) easily add
all reliability properties on the later stages of development, without changing the whole structure of the code. Also, I
tried to make the solution easily extendable for the new features. Yet some peculiarities are still not covered
by this solution, and this will be discussed below.

### Application Domain and Bounded Context

Potentially, User may have more than one Account. Money could be transferred to any other Account, no matter which User
own these Accounts. This way, our Service do not need to know anything about Users to make all operations. By following,
"need-to-know" principle, I am excluding User completely from the bounded context of our Service. The is no such
entity in our domain model.

Requirements does not have any notes about multi-currency support. Potentially, every Account may be multi-currency or
single-currency. For simplicity reasons, I assume all accounts are single-currency, and we are transferring money only
between same-currency accounts. But I do not introduce currencies into our domain model since our service can make
transfers on any currency, and this consistency check may be done at the same place where the system decides whether
some User authorized to make a transfer or withdrawal - outside our Service.

### Deterministic Behaviour

Some serious concerns raised regarding UUIDs. Please see my comments in my code at
[org.example.hometask.state.PendingWithdrawalOperation.generateUuid()](service%2Fsrc%2Fmain%2Fjava%2Forg%2Fexample%2Fhometask%2Fstate%2FPendingWithdrawalOperation.java).
I solved problems with deterministic random numbers before, but it may be out of scope of my homework now.

Also, due to absence of durability, I did not implement logic for retrying external requests
to [WithdrawalService.java](service%2Fsrc%2Fmain%2Fjava%2Forg%2Fexample%2Fhometask%2Fexternal%2FWithdrawalService.java)
after the Service restarts and finishes replay.

### Aeron Transport Reliability and Indempotence

Previously, I had no real experience with Aeron Transport. The code in my project was derived from Aeron Cookbook.
The official docs are saying the transport is reliable between media drivers. But it is not durable in the absence of
Archive. On the other hand, we have no durability requirement. Therefore, I postponed the question regarding
"exactly-once" message delivery until durability becomes a requirement. But the business logic of the Service does not
handle idempotence since it may be offloaded to lower level when durability will be implemented.

Implementing the guaranteed delivery and indempotence on the business logic layer is a very bad thing and should be
avoided whenever is possible. Only such unfortunate transports like HTTP APIs are requiring this. Guaranteed delivery
and indempotence should be a responsibility of the underlying transport and should not pollute the code of business
logic.

### External API for Withdrawals

I considered the case
when [WithdrawalServiceStub.java](service%2Fsrc%2Fmain%2Fjava%2Forg%2Fexample%2Fhometask%2Fexternal%2FWithdrawalServiceStub.java)
may be replaced with some external HTTP API. This or other way,
using [WithdrawalService.java](service%2Fsrc%2Fmain%2Fjava%2Forg%2Fexample%2Fhometask%2Fexternal%2FWithdrawalService.java)
requires IO and more likely non-deterministic. It also may be very slow and unreliable. Due to all these reasons, I
moved it out of the deterministic state machine. In case if any calls
to [WithdrawalService.java](service%2Fsrc%2Fmain%2Fjava%2Forg%2Fexample%2Fhometask%2Fexternal%2FWithdrawalService.java)
may take more than millisecond latencies, we may be needed to wrap it into a separate thread pool. Increasing ring
buffer instead of this would be a bad idea for cache locality reasons.
