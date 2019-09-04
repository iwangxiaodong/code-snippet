package edu.application;

import edu.domain.model.auth.AuthService;
import edu.ui.web.jsf.ViewService;
import edu.ui.web.jsf.ViewServiceImpl;
import edu.ui.web.rest.RestfulRoleFilter;
import javax.ws.rs.ApplicationPath;

import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
//import org.glassfish.jersey.moxy.xml.MoxyXmlFeature;

@ApplicationPath("/-/api/rest/*")
public class JerseyRestConfig extends ResourceConfig {

    public JerseyRestConfig() {

        register(new AbstractBinder() {

            @Override
            protected void configure() {
                System.out.println("JerseyRestConfig.configure()");
                //标注过@RequestScoped和@Named的不用在此绑定直接使用。
                // bind（实现）.to(接口),与Guice正好颠倒
                //bind(RestInterceptorService.class).to(InterceptionService.class).in(Singleton.class);
                bind(ViewServiceImpl.class).to(ViewService.class);
                bindAsContract(AuthService.class);//绑定自身实例/非单例

                //bindAsContract(MyInjectableSingleton.class).in(Singleton.class);
                //bind(YamlImpl.class).to(ObjectSerialization.class);
                //bind(EventStoreImpl.class).to(EventStore.class);
//                TypeDescription.Generic genericImpl
//                        = TypeDescription.Generic.Builder.parameterizedType(EventRepository.class, Tool.class, ToolId.class).build();
//                Class<?> rcImpl = new ByteBuddy()
//                        .subclass(genericImpl)
//                        .make()
//                        .load(this.getClass().getClassLoader())
//                        .getLoaded();
//
//                TypeDescription.Generic genericInterface
//                        = TypeDescription.Generic.Builder.parameterizedType(AggregateRepository.class, Tool.class, ToolId.class).build();
//                Class<?> rc = new ByteBuddy()
//                        .subclass(genericInterface)
//                        .make()
//                        .load(this.getClass().getClassLoader())
//                        .getLoaded();
//
//                System.out.println(rc.getName());
                //bind(rcImpl).to(rc).in(RequestScoped.class);
//                bind(new TypeLiteral<EventRepository<Tool, ToolId>>() {
//                }.getClass()).to(new TypeLiteral<AggregateRepository<Tool, Object>>() {
//                }.getClass()).in(RequestScoped.class);
            }
        });

//        //  以下日志配置可以正常使用，暂时注释掉
//        Logger loggerObj = Logger.getLogger(this.getClass().getName());
//        String tmpDir = System.getProperty("java.io.tmpdir");
//        Path logFilePath = Paths.get(tmpDir, this.getClass().getName() + ".log");
//        FileHandler logHandleObj;
//        try {
//            logHandleObj = new FileHandler(logFilePath.toString(), 0, 1, true);
//            //logHandleObj.setFormatter(new SimpleFormatter());
//            logHandleObj.setFormatter(new Formatter() {
//                @Override
//                public String format(LogRecord record) {
//                    return record.getLevel() + ":" + record.getMessage() + "\n";
//                }
//            });
//
//            loggerObj.setUseParentHandlers(false);
//            loggerObj.addHandler(logHandleObj);
//            loggerObj.setLevel(Level.ALL);
//        } catch (IOException | SecurityException ex) {
//            Logger.getGlobal().severe(ex.toString());
//        }
//        register(new LoggingFeature(loggerObj, Level.ALL, LoggingFeature.Verbosity.PAYLOAD_ANY, LoggingFeature.DEFAULT_MAX_ENTITY_SIZE));
        packages(RestfulRoleFilter.class.getPackageName());

        register(JacksonFeature.class);// JSON
        //register(new MoxyXmlFeature());// XML
        // register(JsonProcessingFeature.class);
        register(MultiPartFeature.class);  //for @FormDataParam
        register(RolesAllowedDynamicFeature.class);//角色控制

        System.out.println("JerseyConfig...");

    }

}
