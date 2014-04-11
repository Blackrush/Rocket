package org.rocket.network.netty;

import io.netty.channel.Channel;
import org.fungsi.Unit;
import org.fungsi.concurrent.Future;
import org.rocket.network.NetworkCommand;

import java.time.Duration;
import java.util.Objects;

import static org.rocket.network.netty.ChannelFutures.toFungsi;

public final class CloseCommand implements NetworkCommand {
	private final Channel channel;

	public CloseCommand(Channel channel) {
		this.channel = Objects.requireNonNull(channel, "channel");
	}

	@Override
	public void now() {
		channel.close().awaitUninterruptibly();
	}

	@Override
	public void now(Duration max) {
		channel.close().awaitUninterruptibly(max.toMillis());
	}

	@Override
	public Future<Unit> async() {
		return toFungsi(channel.close());
	}

	@Override
	public Future<Unit> async(Duration max) { // not supported
		return async();
	}
}
