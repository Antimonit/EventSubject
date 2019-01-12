package me.khol.reactive.subjects;

import org.junit.Test;

import io.reactivex.observers.TestObserver;

public class SubjectsTests {

	@Test
	public void testEventSubject() {
		EventSubject<Integer> subject = EventSubject.create();

		TestObserver<Integer> to1 = subject.test();

		// fresh EventSubjects are empty
		to1.assertEmpty();

		TestObserver<Integer> to2 = subject.test();

		// A EventSubject only allows one Observer subscribed at a time
		to2.assertFailure(IllegalStateException.class);

		subject.onNext(1);
		to1.assertValue(1);

		subject.onNext(2);
		to1.assertValues(1, 2);

		to1.dispose();

		// a EventSubject caches events until an Observer subscribes
		subject.onNext(3);
		subject.onNext(4);

		TestObserver<Integer> to3 = subject.test();

		// the cached events are emitted in order
		to3.assertValues(3, 4);

		subject.onComplete();
		to3.assertResult(3, 4);
	}

}
