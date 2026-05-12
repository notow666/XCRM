package cn.cordys.file.config;

import cn.cordys.file.engine.DefaultRepositoryDir;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 将本地文件仓库根目录绑定到配置项 {@code cordys.file.root-dir}。
 */
@Component
public class FileRepositoryRootConfigurer {

    @Value("${cordys.file.root-dir}")
    private String repositoryRoot;

    @PostConstruct
    public void apply() {
        DefaultRepositoryDir.setRepositoryRoot(repositoryRoot);
    }
}
