package ws.wamp.jawampa.roles;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;

import rx.Observer;
import rx.subjects.PublishSubject;
import ws.wamp.jawampa.PubSubData;
import ws.wamp.jawampa.ids.RequestId;
import ws.wamp.jawampa.ids.SubscriptionId;
import ws.wamp.jawampa.io.BaseClient;
import ws.wamp.jawampa.messages.ErrorMessage;
import ws.wamp.jawampa.messages.EventMessage;
import ws.wamp.jawampa.messages.SubscribeMessage;
import ws.wamp.jawampa.messages.SubscribedMessage;
import ws.wamp.jawampa.messages.UnsubscribeMessage;
import ws.wamp.jawampa.messages.UnsubscribedMessage;
import ws.wamp.jawampa.messages.WampMessage;
import ws.wamp.jawampa.messages.handling.BaseMessageHandler;
import ws.wamp.jawampa.roles.RequestTracker.MessageFactory;

public class Subscriber extends BaseMessageHandler {
    private final BaseClient baseClient;

    private final RequestTracker<SubscriptionId> registrationTracker;
    private final RequestTracker<Void> unregistrationTracker;

    private final Map<SubscriptionId, PublishSubject<PubSubData>> subscriptionId2publishSubject;
    private final BidiMap<String, SubscriptionId> topic2subscriptionId;

    public Subscriber( BaseClient baseClient ) {
        this.baseClient = baseClient;

        registrationTracker = new RequestTracker<SubscriptionId>( baseClient );
        unregistrationTracker = new RequestTracker<Void>( baseClient );

        subscriptionId2publishSubject = new HashMap<SubscriptionId, PublishSubject<PubSubData>>();
        topic2subscriptionId = new DualHashBidiMap<String, SubscriptionId>();
    }

    public void subscribe( final String topic, final PublishSubject<PubSubData> resultSubject ) {
        PublishSubject<SubscriptionId> registrationSubject = PublishSubject.create();
        registrationSubject.subscribe( new Observer<SubscriptionId>() {
            @Override
            public void onNext( SubscriptionId subscriptionId ) {
                subscriptionId2publishSubject.put( subscriptionId, resultSubject );
                topic2subscriptionId.put( topic, subscriptionId );
            }

            @Override
            public void onCompleted() {
                // intentionally empty
            }

            @Override
            public void onError( Throwable e ) {
                resultSubject.onError( e );
            }
        } );
        registrationTracker.sendRequest( registrationSubject, new MessageFactory() {
            @Override
            public WampMessage fromRequestId( RequestId requestId ) {
                return new SubscribeMessage( requestId, null, topic );
            }
        } );
    }

    @Override
    public void onSubscribed( SubscribedMessage msg ) {
        registrationTracker.onSuccess( msg.requestId, msg.subscriptionId );
    }

    @Override
    public void onSubscribeError( ErrorMessage msg ) {
        registrationTracker.onError( msg );
    }

    @Override
    public void onEvent( EventMessage msg ) {
        if ( subscriptionId2publishSubject.containsKey( msg.subscriptionId ) ) {
            subscriptionId2publishSubject.get( msg.subscriptionId )
                                         .onNext( new PubSubData( msg.arguments,
                                                                  msg.argumentsKw ) );
        } else {
            baseClient.onProtocolError();
        }
    }

    public void unsubscribe( final String topic, final PublishSubject<Void> resultSubject ) {
        PublishSubject<Void> unregistrationSubject = PublishSubject.create();
        unregistrationSubject.subscribe( new Observer<Void>() {
            @Override
            public void onNext( Void t ) {
                // intentionally empty
            }

            @Override
            public void onCompleted() {
                SubscriptionId subscriptionId = topic2subscriptionId.get( topic );
                PublishSubject<PubSubData> publishSubject = subscriptionId2publishSubject.get( subscriptionId );

                subscriptionId2publishSubject.remove( subscriptionId );
                topic2subscriptionId.remove( topic );

                resultSubject.onCompleted();
                publishSubject.onCompleted();
            }

            @Override
            public void onError( Throwable e ) {
                resultSubject.onError( e );
            }
        } );
        unregistrationTracker.sendRequest( unregistrationSubject, new MessageFactory() {
            @Override
            public WampMessage fromRequestId( RequestId requestId ) {
                return new UnsubscribeMessage( requestId,
                                               topic2subscriptionId.get( topic ) );
            }
        } );
    }

    @Override
    public void onUnsubscribed( UnsubscribedMessage msg ) {
        unregistrationTracker.onSuccess( msg.requestId, null );
    }

    @Override
    public void onUnsubscribeError( ErrorMessage msg ) {
        unregistrationTracker.onError( msg );
    }
}