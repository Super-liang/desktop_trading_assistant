package com.tradingassistant.config;

import com.tradingassistant.auth.User;
import com.tradingassistant.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final AppProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AppProperties properties, UserRepository users,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.admin().email();
        String password = properties.admin().password();
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.info("未配置初始管理员，跳过引导创建");
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("ADMIN_PASSWORD 至少需要 12 个字符");
        }
        String normalized = email.strip().toLowerCase(java.util.Locale.ROOT);
        if (!users.existsByEmail(normalized)) {
            users.save(new User(normalized, "系统管理员", passwordEncoder.encode(password),
                    User.Role.ADMIN));
            log.info("已创建初始管理员账号：{}", normalized);
        }
    }
}

