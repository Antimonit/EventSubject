[ ![Download](https://api.bintray.com/packages/antimonit/EventSubject/EventSubject/images/download.svg) ](https://bintray.com/antimonit/EventSubject/EventSubject/_latestVersion)

# EventSubject

`BehaviorSubject` is often used to hold the most recent **state** as it always provides the latest 
value it receives.

This is often utilized with MVVM architecture in Android where ViewModel define its **state** as 
a `BehaviorSubject` to which View subscribes to observe the latest changes in **state**. This is 
done mainly because ViewModel's lifecycle is longer than that of View and we need to be able to 
unsubscribe and resubscribe whenever there is a configuration change.

But not only the **state** needs to be communicated to View. Often times there are situations where 
we need to send a signal to View, such as a command to open a confirmation dialog. We can call such 
signals **events**. There are notable differences between the two:
*  A **state** has a timespan — it is valid for a period of time. Usually we are interested only in 
the latest **state**.
*  An **event** does not have a timespan — it occurs and immediately is over. Usually we are 
interested in all **events**.

`BehaviorSubject` is a great fit for **states** but not so much for **events** because it emits
a value *at least once*. We don't want to process an **event** again when we resubscribe to the 
subject.

`PublishSubject` is more suitable for **events** in a sense that it does not emit any values upon 
resubscription. But it emits the value *at most once*. An **event** is lost if it occurs when there 
is no subscriber.

`EventSubject` is specifically designed to emit each value *exactly once*. Whenever no Observer is 
observing, **events** are being queued up. That ensures no **events** are lost. Whenever a new 
Observer subscribes, all queued up **events** are replayed and removed from the queue. That ensures 
no **event** is processed twice. While an Observer is subscribed all **events** are relayed to the 
Observer directly.

## Implementation

Internally, `EventSubject` is similar to
<a href="http://reactivex.io/RxJava/javadoc/io/reactivex/subjects/UnicastSubject.html">UnicastSubject</a>.
Just like `UnicastSubject`, events are queued up whenever no Observer is subscribed. Whenever a new 
Observer subscribes to this, the queued up events are replayed and the queue subsequently emptied. 
While an Observer is subscribed all emission are relayed to the Observer directly.

`UnicastSubject` allows only a single Subscriber during its lifespan. `EventSubject` relaxes this 
in a sense that it allows only one Observer to be subscribed at a time but when it unsubscribes, 
another one is allowed to resubscribe to this subject again.

<img width="640" height="370" src="EventSubject.png" alt="">

If an Observer attempts to subscribe to an `EventSubject` that already has another Observer 
subscribed already, the Observer attempting to subscribe will receive an `IllegalStateException`.

All other properties of this `EventSubject` are the same as of `UnicastSubject`.

## Dependency
Library can be added as a dependency from `jcenter` repository:
```
dependencies {
    implementation 'me.khol:eventsubject:1.0.0'
}
```
