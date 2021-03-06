package mingzuozhibi.action;

import mingzuozhibi.persist.core.User;
import mingzuozhibi.persist.core.UserRepository;
import mingzuozhibi.support.JsonArg;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

@RestController
public class SessionController {

    private static final String CONTENT_TYPE = "application/json;charset=UTF-8";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDetailsService userDetailsService;

    private Logger logger = LoggerFactory.getLogger(SessionController.class);

    @GetMapping(value = "/api/session", produces = CONTENT_TYPE)
    public String status() {
        logger.info("状态获取: 正在检测登入状态");

        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            if (!"anonymousUser".equals(username)) {
                logger.info("状态获取: 检测到已登入用户, username={}", username);
                JSONObject object = new JSONObject();
                object.put("success", true);
                object.put("username", username);
                putAuthority(object, authentication);
                return object.toString();
            } else {
                logger.info("状态获取: 检测到匿名用户");
                return simpleResult(false);
            }
        } else {
            logger.info("状态获取: 未检测到已登入状态");
            return simpleResult(false);
        }
    }

    private void putAuthority(JSONObject object, Authentication authentication) {
        authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .reduce((s1, s2) -> s1 + "," + s2)
                .ifPresent(roles -> object.put("roles", roles));
    }

    private String simpleResult(boolean success) {
        JSONObject object = new JSONObject();
        object.put("success", success);
        return object.toString();
    }

    @PostMapping(value = "/api/session", produces = CONTENT_TYPE)
    public String login(@JsonArg("$.username") String username,
                        @JsonArg("$.password") String password) {
        logger.info("用户登入: username={}, password=******", username);

        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            if (userDetails.getPassword().equals(password) && userDetails.isEnabled()) {
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails.getUsername(), userDetails.getPassword(), userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                onLoginSuccess(username);
                logger.info("用户登入: 用户已成功登入, username={}", username);
                return simpleResult(true);
            } else {
                logger.info("用户登入: 未能成功登入, username={}", username);
                return simpleResult(false);
            }
        } catch (UsernameNotFoundException ignored) {
            logger.info("用户登入: 未找到该用户, username={}", username);
            return simpleResult(false);
        }
    }

    private void onLoginSuccess(String username) {
        User user = userRepository.findByUsername(username);
        user.setLastLoggedin(new Date());
        userRepository.save(user);
    }

    @DeleteMapping(value = "/api/session", produces = CONTENT_TYPE)
    public String logout() {
        logger.info("用户登出: 正在检测登入状态");

        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            context.setAuthentication(null);
            if (!"anonymousUser".equals(username)) {
                logger.info("用户登出: 用户已成功登出, username={}", username);
                return simpleResult(true);
            } else {
                logger.info("用户登出: 检测到匿名用户");
                return simpleResult(true);
            }
        } else {
            logger.info("用户登出: 未检测到已登入状态");
            return simpleResult(false);
        }
    }

}
