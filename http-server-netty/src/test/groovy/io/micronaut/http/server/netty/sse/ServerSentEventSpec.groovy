/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.netty.sse

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.sse.Event
import io.reactivex.Flowable
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux

import java.time.Duration
import java.time.temporal.ChronoUnit

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ServerSentEventSpec extends AbstractMicronautSpec {

    void "test consume event stream object"() {
        given:
        SseClient client = embeddedServer.applicationContext.getBean(SseClient)
        List<Event<Foo>> events = client.object().collectList().block()

        expect:
        events.size() == 4
        events.first().data == new Foo(name: "Foo 1",age:11)
    }

    void "test consume event stream string"() {
        given:
        SseClient client = embeddedServer.applicationContext.getBean(SseClient)
        List<Event<String>> events = client.string().collectList().block()

        expect:
        events.size() == 4
        events.first().data == 'Foo 1'
    }

    void "test consume rich event stream object"() {
        given:
        SseClient client = embeddedServer.applicationContext.getBean(SseClient)
        List<Event<Foo>> events = client.rich().collectList().block()

        expect:
        events.size() == 4
        events.first().data == new Foo(name: "Foo 1",age:11)
        events.first().id == "1"
        events.first().retry == Duration.ofMinutes(2)
    }

    void "test receive error from supplier"() {
        given:
        SseClient client = embeddedServer.applicationContext.getBean(SseClient)

        when:
        List<Event<String>> events = client.exception().collectList().block()

        then:
        events.size() == 0
    }

    @Client('/sse')
    static interface SseClient {

        @Get(value = '/object', processes = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<Foo>> object()

        @Get(value = '/string', processes = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> string()

        @Get(value = '/rich', processes = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<Foo>> rich()

        @Get(value = '/exception', processes = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> exception()

        @Get(value = '/on-error', processes = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> onError()
    }

    @Controller('/sse')
    @Requires(property = 'spec.name', value = 'ServerSentEventSpec')
    static class SseController {

        @Get('/object')
        Publisher<Event> object() {
            int i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(Event.of(new Foo(name: "Foo $i", age: i + 10)))
                }
                else {
                    emitter.onComplete()
                }
            })
        }

        @Get('/rich')
        Publisher<Event> rich() {
            Integer i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(
                    Event.of(new Foo(name: "Foo $i", age: i + 10))
                            .name('foo')
                            .id(i.toString())
                            .comment("Foo Comment $i")
                            .retry(Duration.of(2, ChronoUnit.MINUTES)))
                }
                else {
                    emitter.onComplete()
                }
            })
        }

        @Get('/string')
        Publisher<Event> string() {
            int i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(Event.of("Foo $i"))
                }
                else {
                    emitter.onComplete()
                }
            })
        }

        @Get('/exception')
        Publisher<Event> exception() {
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                throw new RuntimeException("bad things happened")
            })
        }

        @Get('on-error')
        Publisher<Event> onError() {
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                emitter.onError(new RuntimeException("bad things happened"))
            })
        }
    }

    @EqualsAndHashCode
    @ToString(includePackage = false)
    static class Foo {
        String name
        Integer age
    }
}
