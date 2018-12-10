import java.time.Clock

import config.{PlayBasedServiceConfiguration, ServiceConfiguration}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class UpscanInitiateModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    Seq(
      bind[ServiceConfiguration].to[PlayBasedServiceConfiguration],
      bind[Clock].toInstance(Clock.systemDefaultZone())
    )
}
