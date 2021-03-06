package com.twitter.finagle.netty4.channel

import com.twitter.finagle.netty4.ssl.Netty4SslHandler
import com.twitter.finagle.param._
import com.twitter.finagle.transport.Transport
import com.twitter.finagle.Stack
import io.netty.channel._
import java.util.logging.Level

private[netty4] object Netty4RawServerChannelInitializer {
  val ChannelLoggerHandlerKey = "channel logger"
  val ChannelStatsHandlerKey = "channel stats"
}

/**
 * Server channel initialization logic for the part of the netty pipeline that
 * deals with raw bytes.
 *
 * @param params [[Stack.Params]] to configure the `Channel`.
 */
private[netty4] class Netty4RawServerChannelInitializer(
    params: Stack.Params)
  extends ChannelInitializer[Channel] {

  import Netty4RawServerChannelInitializer._

  private[this] val Logger(logger) = params[Logger]
  private[this] val Label(label) = params[Label]
  private[this] val Stats(stats) = params[Stats]

  private[this] val channelStatsHandler =
    if (!stats.isNull) Some(new ChannelStatsHandler(stats)) else None

  private[this] val channelSnooper =
    if (params[Transport.Verbose].enabled)
      Some(ChannelSnooper.byteSnooper(label)(logger.log(Level.INFO, _, _)))
    else
      None

  override def initChannel(ch: Channel): Unit = {
    // first => last
    // - a request flies from first to last
    // - a response flies from last to first
    //
    // ssl => channel stats => channel snooper => write timeout => read timeout => req stats => ..
    // .. => exceptions => finagle

    val pipeline = ch.pipeline

    channelSnooper.foreach(pipeline.addFirst(ChannelLoggerHandlerKey, _))
    channelStatsHandler.foreach(pipeline.addFirst(ChannelStatsHandlerKey, _))

    // Add SslHandler to the pipeline.
    pipeline.addFirst("tls init", new Netty4SslHandler(params))

    // Copy direct byte buffers onto heap before doing anything else.
    pipeline.addFirst("direct to heap", DirectToHeapInboundHandler)
  }
}
