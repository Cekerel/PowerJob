package com.github.kfcfans.powerjob.server.akka;

import akka.actor.*;
import akka.routing.RoundRobinPool;
import com.github.kfcfans.powerjob.common.RemoteConstant;
import com.github.kfcfans.powerjob.common.utils.NetUtils;
import com.github.kfcfans.powerjob.server.akka.actors.FriendActor;
import com.github.kfcfans.powerjob.server.akka.actors.ServerActor;
import com.github.kfcfans.powerjob.server.akka.actors.ServerTroubleshootingActor;
import com.github.kfcfans.powerjob.server.common.PowerJobServerConfigKey;
import com.github.kfcfans.powerjob.server.common.utils.PropertyUtils;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Properties;

/**
 * 服务端 ActorSystem 启动器
 *
 * @author tjq
 * @since 2020/4/2
 */
@Slf4j
public class OhMyServer {

    public static ActorSystem actorSystem;
    @Getter
    private static String actorSystemAddress;

    private static final String AKKA_PATH = "akka://%s@%s/user/%s";

    public static void init() {

        Stopwatch stopwatch = Stopwatch.createStarted();
        log.info("[OhMyServer] OhMyServer's akka system start to bootstrap...");

        // 忽略了一个问题，机器是没办法访问外网的，除非架设自己的NTP服务器
        // TimeUtils.check();

        // 解析配置文件
        PropertyUtils.init();
        Properties properties = PropertyUtils.getProperties();
        int port = Integer.parseInt(properties.getProperty(PowerJobServerConfigKey.AKKA_PORT, "10086"));

        // 启动 ActorSystem
        Map<String, Object> overrideConfig = Maps.newHashMap();
        String localIP = NetUtils.getLocalHost();
        overrideConfig.put("akka.remote.artery.canonical.hostname", localIP);
        overrideConfig.put("akka.remote.artery.canonical.port", port);
        actorSystemAddress = localIP + ":" + port;
        log.info("[OhMyWorker] akka-remote server address: {}", actorSystemAddress);

        // ConfigFactory.load用来加载指定路径下的配置文件
        Config akkaBasicConfig = ConfigFactory.load(RemoteConstant.SERVER_AKKA_CONFIG_NAME);

        //将代码中获取到的配置overrideConfig与配置文件中地基础配置合并成最终的AKKA配置
        Config akkaFinalConfig = ConfigFactory.parseMap(overrideConfig).withFallback(akkaBasicConfig);


        actorSystem = ActorSystem.create(RemoteConstant.SERVER_ACTOR_SYSTEM_NAME, akkaFinalConfig);
        actorSystem.actorOf(Props.create(ServerActor.class)
                .withDispatcher("akka.server-actor-dispatcher")
                .withRouter(new RoundRobinPool(Runtime.getRuntime().availableProcessors() * 4)), RemoteConstant.SERVER_ACTOR_NAME);
        actorSystem.actorOf(Props.create(FriendActor.class), RemoteConstant.SERVER_FRIEND_ACTOR_NAME);

        // 处理系统中产生的异常情况
        ActorRef troubleshootingActor = actorSystem.actorOf(Props.create(ServerTroubleshootingActor.class), RemoteConstant.SERVER_TROUBLESHOOTING_ACTOR_NAME);
        actorSystem.eventStream().subscribe(troubleshootingActor, DeadLetter.class);

        log.info("[OhMyServer] OhMyServer's akka system start successfully, using time {}.", stopwatch);
    }

    /**
     * 获取 ServerActor 的 ActorSelection
     * @param address IP:port
     * @return ActorSelection
     */
    public static ActorSelection getFriendActor(String address) {
        String path = String.format(AKKA_PATH, RemoteConstant.SERVER_ACTOR_SYSTEM_NAME, address, RemoteConstant.SERVER_FRIEND_ACTOR_NAME);
        return actorSystem.actorSelection(path);
    }

    /**
     *  获取TaskTrackActor 的 ActorSelection
     * @param address IP:port
     * @return ActorSelection
     */
    public static ActorSelection getTaskTrackerActor(String address) {
        String path = String.format(AKKA_PATH, RemoteConstant.WORKER_ACTOR_SYSTEM_NAME, address, RemoteConstant.Task_TRACKER_ACTOR_NAME);
        return actorSystem.actorSelection(path);
    }

    /**
     *  获取WorkerActor 的 ActorSelection
     * @param address IP:port
     * @return  ActorSelection
     */
    public static ActorSelection getWorkerActor(String address) {
        String path = String.format(AKKA_PATH, RemoteConstant.WORKER_ACTOR_SYSTEM_NAME, address, RemoteConstant.WORKER_ACTOR_NAME);
        return actorSystem.actorSelection(path);
    }
}
