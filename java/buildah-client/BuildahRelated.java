package our.cloud.infrastructure;

import io.jshift.buildah.api.BuildahConfiguration;
import io.jshift.buildah.core.Buildah;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 *
 * @author xiaodong
 */
public class BuildahRelated {

    static Buildah bah;

    static {
        BuildahConfiguration config = new BuildahConfiguration();
        config.setLocalBuildah(Paths.get("/usr/bin/buildah"));
        config.setLocalRunc(Paths.get("/usr/bin/runc"));
        bah = new Buildah(config);
    }

    public static void demo() {
        bah.version().build().execute();
        System.out.println(new Date());
        bah.pull("busybox").build().execute();
        System.out.println(new Date());
        bah.listImages().build().execute();
        System.out.println(new Date());
    }

    //  buildah --storage-driver vfs bud --file Dockerfile -t temp123456789 /tmp/bud/
    //  本方法是同步阻塞的
    public static void buildDockerfile(String dir, String imageName) {
        List<String> dockerfileList = Arrays.asList("Dockerfile");
        List<String> ea = Arrays.asList("--storage-driver", "vfs");
        var cmd = bah.bud(dir).withExtraArguments(ea).dockerfileList(dockerfileList).targetImage(imageName).build();
        System.out.println(String.join(" ", cmd.getCliCommand()));
        cmd.execute();
    }

    public static void pushImage(String imageId, String imageName) {
        pushImage(imageId, imageName, null, null);
    }

    //  buildah --storage-driver vfs push --tls-verify=false --creds=username:密码 temp-1588360599648 registry.cn-hangzhou.aliyuncs.com/x/example-temp:temp-1588360599648
    public static void pushImage(String imageId, String imageName, String username, String password) {
        List<String> ea = Arrays.asList("--storage-driver", "vfs");
        var b = bah.push(imageId).withExtraArguments(ea);
        if (username != null && !username.isBlank()) {
            b = b.creds(username + ":" + password);//   todo - 后续考虑是否处理值中存在冒号的情况
        }
        var cmd = b.registry(imageName).build();
        System.out.println(String.join(" ", cmd.getCliCommand()));
        cmd.execute();
    }
}
