package me.khol.reactive.subjects;

import org.junit.Before;
import org.junit.Test;

import io.reactivex.rxjava3.observers.TestObserver;

public class SubjectsTests {

	private EventSubject<Integer> subject;

	@Before
	public void setUp() {
		subject = EventSubject.create();
	}

	@Test
	public void testOnlyOneSubscriber() {
		TestObserver<Integer> to1 = subject.test();
		TestObserver<Integer> to2 = subject.test();
		to2.assertFailure(IllegalStateException.class);
	}

	@Test
	public void testDefaultEmpty() {
		TestObserver<Integer> to1 = subject.test();
		to1.assertEmpty();
	}

	@Test
	public void testImmediateValues() {
		TestObserver<Integer> to1 = subject.test();

		subject.onNext(1);
		to1.assertValue(1);

		subject.onNext(2);
		to1.assertValues(1, 2);
	}

	@Test
	public void testQueuedValues() {
		subject.onNext(1);
		subject.onNext(2);

		TestObserver<Integer> to1 = subject.test();
		to1.assertValues(1, 2);
	}

	@Test
	public void testResubscribe() {
		TestObserver<Integer> to1 = subject.test();
		subject.onNext(1);
		to1.assertValues(1);

		to1.dispose();

		TestObserver<Integer> to2 = subject.test();
		subject.onNext(2);
		to2.assertValues(2);
	}
}
