package ca.uhn.fhir.jpa.subscription.module.channel;

import ca.uhn.fhir.jpa.model.entity.ModelConfig;
import ca.uhn.fhir.jpa.subscription.module.cache.ActiveSubscription;
import ca.uhn.fhir.jpa.subscription.module.cache.SubscriptionRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;

@Component
public class SubscriptionChannelRegistry {
	private static final Logger ourLog = LoggerFactory.getLogger(SubscriptionRegistry.class);

	private final SubscriptionChannelCache mySubscriptionChannelCache = new SubscriptionChannelCache();
	// This map is a reference count so we know to destroy the channel when there are no more active subscriptions using it
	// Key Channel Name, Value Subscription Id
	private final Multimap<String, String> myActiveSubscriptionByChannelName = MultimapBuilder.hashKeys().arrayListValues().build();

	@Autowired
	SubscriptionDeliveryHandlerFactory mySubscriptionDeliveryHandlerFactory;
	@Autowired
	SubscriptionChannelFactory mySubscriptionDeliveryChannelFactory;
	@Autowired
	ModelConfig myModelConfig;

	public void add(ActiveSubscription theActiveSubscription) {
		if (!myModelConfig.isSubscriptionMatchingEnabled()) {
			return;
		}
		String channelName = theActiveSubscription.getChannelName();
		ourLog.info("Adding subscription {} to channel {}", theActiveSubscription.getId(), channelName);
		myActiveSubscriptionByChannelName.put(channelName, theActiveSubscription.getId());

		if (mySubscriptionChannelCache.containsKey(channelName)) {
			ourLog.info("Channel {} already exists.  Not creating.", channelName);
			return;
		}

		ISubscribableChannel deliveryChannel;
		Optional<MessageHandler> deliveryHandler;

		deliveryChannel = mySubscriptionDeliveryChannelFactory.newDeliveryChannel(channelName);
		deliveryHandler = mySubscriptionDeliveryHandlerFactory.createDeliveryHandler(theActiveSubscription.getChannelType());

		SubscriptionChannelWithHandlers subscriptionChannelWithHandlers = new SubscriptionChannelWithHandlers(channelName, deliveryChannel);
		deliveryHandler.ifPresent(subscriptionChannelWithHandlers::addHandler);
		mySubscriptionChannelCache.put(channelName, subscriptionChannelWithHandlers);
	}

	public void remove(ActiveSubscription theActiveSubscription) {
		if (!myModelConfig.isSubscriptionMatchingEnabled()) {
			return;
		}
		String channelName = theActiveSubscription.getChannelName();
		ourLog.info("Removing subscription {} from channel {}: {}", theActiveSubscription.getId() ,channelName, myActiveSubscriptionByChannelName);
		boolean removed = myActiveSubscriptionByChannelName.remove(channelName, theActiveSubscription.getId());
		if (!removed) {
			ourLog.warn("Failed to remove subscription {} from channel {}", theActiveSubscription.getId() ,channelName);
		}

		// This was the last one.  Shut down the channel
		if (!myActiveSubscriptionByChannelName.containsKey(channelName)) {
			SubscriptionChannelWithHandlers channel = mySubscriptionChannelCache.get(channelName);
			if (channel != null) {
				channel.close();
			}
			mySubscriptionChannelCache.remove(channelName);
		}
	}

	public SubscriptionChannelWithHandlers get(String theChannelName) {
		return mySubscriptionChannelCache.get(theChannelName);
	}

	public int size() {
		return mySubscriptionChannelCache.size();
	}

	@VisibleForTesting
	public void logForUnitTest() {
		ourLog.info("{} Channels: {}", this, size());
		mySubscriptionChannelCache.logForUnitTest();
		for (String key : myActiveSubscriptionByChannelName.keySet()) {
			Collection<String> list = myActiveSubscriptionByChannelName.get(key);
			for (String value : list) {
				ourLog.info("ActiveSubscriptionByChannelName {}: {}", key, value);
			}
		}
	}
}