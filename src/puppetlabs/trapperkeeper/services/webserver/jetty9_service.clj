(ns puppetlabs.trapperkeeper.services.webserver.jetty9-service
  (:require
    [clojure.tools.logging :as log]

    [puppetlabs.trapperkeeper.services.webserver.jetty9-config :as config]
    [puppetlabs.trapperkeeper.services.webserver.jetty9-core :as core]
    [puppetlabs.trapperkeeper.core :refer [defservice]]))


(def context-type "Context Handler")
(def ring-type "Ring Handler")
(def servlet-type "Servlet Handler")
(def war-type "War Handler")
(def proxy-type "Proxy")
;; TODO: this should probably be moved to a separate jar that can be used as
;; a dependency for all webserver service implementations
(defprotocol WebserverService
  (add-context-handler [this base-path context-path] [this base-path context-path context-listeners])
  (add-ring-handler [this handler path])
  (add-servlet-handler [this servlet path] [this servlet path servlet-init-params])
  (add-war-handler [this war path])
  (add-proxy-route [this target path] [this target path options])
  (override-webserver-settings! [this overrides])
  (get-registered-endpoints [this])
  (join [this]))

(defservice jetty9-service
  "Provides a Jetty 9 web server as a service"
  WebserverService
  [[:ConfigService get-in-config]]
  (init [this context]
        (log/info "Initializing web server.")
        (assoc context :jetty9-server (core/initialize-context)))

  (start [this context]
         (log/info "Starting web server.")
         (let [config (or (get-in-config [:webserver])
                          ;; Here for backward compatibility with existing projects
                          (get-in-config [:jetty])
                          {})
               webserver (core/start-webserver! (:jetty9-server context) config)]
           (assoc context :jetty9-server webserver)))

  (stop [this context]
        (log/info "Shutting down web server.")
        (if-let [server (:jetty9-server context)]
          (core/shutdown server))
        context)

  (add-context-handler [this base-path context-path]
                       (let [s             ((service-context this) :jetty9-server)
                             state         (:state (:jetty9-server (service-context this)))
                             endpoint-info {:type context-type
                                            :base-path base-path
                                            :endpoint  context-path}]
                         (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                         (core/add-context-handler s base-path context-path)))

  (add-context-handler [this base-path context-path context-listeners]
                       (let [s             ((service-context this) :jetty9-server)
                             state         (:state (:jetty9-server (service-context this)))
                             endpoint-info {:type context-type
                                            :base-path base-path
                                            :endpoint  context-path}]
                         (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                         (core/add-context-handler s base-path context-path context-listeners)))

  (add-ring-handler [this handler path]
                    (let [s             ((service-context this) :jetty9-server)
                          state         (:state (:jetty9-server (service-context this)))
                          endpoint-info {:type ring-type
                                         :endpoint path}]
                      (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                      (core/add-ring-handler s handler path)))

  (add-servlet-handler [this servlet path]
                       (let [s             ((service-context this) :jetty9-server)
                             state         (:state (:jetty9-server (service-context this)))
                             endpoint-info {:type servlet-type
                                            :endpoint path}]
                         (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                         (core/add-servlet-handler s servlet path)))

  (add-servlet-handler [this servlet path servlet-init-params]
                       (let [s             ((service-context this) :jetty9-server)
                             state         (:state (:jetty9-server (service-context this)))
                             endpoint-info {:type servlet-type
                                            :endpoint path}]
                         (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                         (core/add-servlet-handler s servlet path servlet-init-params)))

  (add-war-handler [this war path]
                   (let [s             ((service-context this) :jetty9-server)
                         state         (:state (:jetty9-server (service-context this)))
                         endpoint-info {:type war-type
                                        :war war
                                        :endpoint path}]
                     (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                     (core/add-war-handler s war path)))

  (add-proxy-route [this target path]
                   (let [s             ((service-context this) :jetty9-server)
                         state         (:state (:jetty9-server (service-context this)))
                         endpoint-info {:type proxy-type
                                        :host (:host target)
                                        :port (:port target)
                                        :old-prefix path
                                        :new-prefix (:path target)}]
                     (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                     (core/add-proxy-route s target path {})))

  (add-proxy-route [this target path options]
                   (let [s             ((service-context this) :jetty9-server)
                         state         (:state (:jetty9-server (service-context this)))
                         endpoint-info {:type proxy-type
                                        :host (:host target)
                                        :port (:port target)
                                        :old-prefix path
                                        :new-prefix (:path target)}]
                     (swap! state assoc :endpoints (conj (:endpoints @state) endpoint-info))
                     (core/add-proxy-route s target path options)))

  (override-webserver-settings! [this overrides]
                                (let [s ((service-context this) :jetty9-server)]
                                  (core/override-webserver-settings! s
                                                                     overrides)))

  (get-registered-endpoints [this]
                            (:endpoints @(:state (:jetty9-server (service-context this)))))

  (join [this]
        (let [s ((service-context this) :jetty9-server)]
          (core/join s))))
