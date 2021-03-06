package io.netflow

import io.netflow.netty._
import io.wasted.util._

import io.netty.bootstrap._
import io.netty.channel._
import io.netty.channel.nio._
import io.netty.channel.socket.nio._

import scala.util.{ Try, Success, Failure }

object Server extends App with Logger { PS =>
  override def main(args: Array[String]) { start() }
  private var servers: List[Bootstrap] = List()

  def start() {
    info("Starting up netflow.io version %s", io.netflow.lib.BuildInfo.version)
    Service.start()

    def startListeningFor(what: String, config: String, default: List[String], handler: ChannelHandler) {
      val conf = Config.getStringList(config, default).map(_.split(":"))
      val listeners = conf.map(l => new java.net.InetSocketAddress(l.head, l.last.toInt))

      // Refresh filters from Backend
      Try {
        servers = listeners.map { addr =>
          val srv = new Bootstrap
          val chan = srv.group(new NioEventLoopGroup)
            .localAddress(addr)
            .channel(classOf[NioDatagramChannel])
            .handler(handler)
            .option[java.lang.Integer](ChannelOption.SO_RCVBUF, 1500)
            .option[java.lang.Integer](ChannelOption.UDP_RECEIVE_PACKET_SIZE, 1500)
          srv.bind().sync
          info("Listening for %s on %s:%s", what, addr.getAddress.getHostAddress, addr.getPort)
          srv
        }
      } match {
        case Success(v) => Some(v)
        case Failure(f) =>
          error("Unable to bind for %s to that ip:port combination. Check your configuration.".format(what))
          debug(f)
          stop
          return
      }
    }

    startListeningFor("NetFlow", "netflow.listen", List("0.0.0.0:2055"), NetFlowHandler)
    startListeningFor("sFlow", "sflow.listen", List("0.0.0.0:6343"), SFlowHandler)

    info("Ready")

    // Add Shutdown Hook to cleanly shutdown Netty
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() { PS.stop }
    })
  }

  private def stop() {
    info("Shutting down")

    // Shut down all event loops to terminate all threads.
    Tryo(servers.foreach(_.shutdown))
    servers = List()
    Service.stop()
    info("Shutdown complete")
  }
}

