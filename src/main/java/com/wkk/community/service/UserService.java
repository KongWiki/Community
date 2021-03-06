package com.wkk.community.service;

import com.wkk.community.dao.LoginTicketMapper;
import com.wkk.community.dao.UserMapper;
import com.wkk.community.entity.LoginTicket;
import com.wkk.community.entity.User;
import com.wkk.community.util.CommunityConstant;
import com.wkk.community.util.CommunityUtil;
import com.wkk.community.util.MailClient;
import com.wkk.community.util.RedisKeyUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @Time: 2020/4/29上午11:51
 * @Author: kongwiki
 * @Email: kongwiki@163.com
 */
@Service
public class UserService implements CommunityConstant {
    @Autowired(required = false)
    private UserMapper userMapper;
    //    @Autowired(required = false)
//    private LoginTicketMapper loginTicketMapper;
    @Autowired
    private MailClient mailClient;
    @Autowired
    private TemplateEngine templateEngine;
    @Autowired
    private RedisTemplate redisTemplate;


    //域名
    @Value("${community.path.domain}")
    private String domain;
    // 项目名
    @Value("${server.servlet.context-path}")
    private String contextPath;


    public User findUserById(int id) {
        User user = getCache(id);
        if (user == null) {
            user = initCache(id);
        }
        return user;
//        return userMapper.selectById(id);
    }

    public User findUserByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    public User findUserByName(String name) {
        return userMapper.selectByName(name);
    }

    // 注册业务逻辑处理
    // 添加事物管理
    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRED)
    public Map<String, Object> register(User user) {
        Map<String, Object> map = new HashMap<>();
        // 输入逻辑问题
        if (user == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        if (StringUtils.isBlank(user.getUsername())) {
            map.put("usernameMSG", "账号不能为空");
            return map;
        }
        if (StringUtils.isBlank(user.getPassword())) {
            map.put("passwordMSG", "密码不能为空");
            return map;
        }
        if (StringUtils.isBlank(user.getEmail())) {
            map.put("emailMSG", "邮箱不能为空");
            return map;
        }

        // 判断账号邮箱是否存在
        User u = userMapper.selectByName(user.getUsername());
        if (u != null) {
            map.put("usernameMSG", "该账号已存在");
            return map;
        }
        u = userMapper.selectByEmail(user.getEmail());
        if (u != null) {
            map.put("emailMSG", "邮箱已注册");
            return map;
        }

        //------------
        // 注册用户
        //------------
        user.setSalt(CommunityUtil.generateUUID().substring(0, 5));
        user.setPassword(CommunityUtil.md5(user.getSalt() + user.getPassword()));
        // 默认为普通用户
        user.setType(0);
        // 未激活状态
        user.setStatus(0);
        user.setActivationCode(CommunityUtil.generateUUID());
        user.setHeaderUrl(String.format("http://images.nowcoder.com/head/%dt.png", new Random().nextInt(1000)));
        user.setCreateTime(new Date());
        userMapper.insertUser(user);

//        user = userMapper.selectByName(user.getUsername());
        // 激活邮件
        Context context = new Context();

        context.setVariable("email", user.getEmail());
        // http://localhost:8080/community/activation/{userId}/activeCode
        String url = domain + contextPath + "/activation/" + user.getId() + "/" + user.getActivationCode();
        context.setVariable("url", url);

        String content = templateEngine.process("/mail/activation", context);
        mailClient.sendMail(user.getEmail(), "Wiki社区激活邮件", content);
        return map;

    }

    //激活处理
    public int activation(int userId, String activeCode) {
        User user = userMapper.selectById(userId);
        if (user.getStatus() == 1) {
            return ACTIVATION_REPEAT;

        } else if (user.getActivationCode().equals(activeCode)) {
            userMapper.updateStatus(userId, 1);
            clearCache(userId);
            return ACTIVATION_SUCCESS;
        } else {
            return ACTIVATION_FAILURE;
        }
    }

    // 登录业务逻辑
    public Map<String, Object> login(String username, String password, int expiredSeconds) {
        Map<String, Object> map = new HashMap<>();
        // 空值处理
        if (StringUtils.isBlank(username)) {
            map.put("usernameMSG", "账号不能为空");
            return map;
        }
        if (StringUtils.isBlank(password)) {
            map.put("passwordMSG", "密码不能为空");
            return map;
        }

        // 合法性对比和数据库对别
        // 验证账号
        User user = userMapper.selectByName(username);
        if (user == null) {
            map.put("usernameMSG", "该账号不存在");
            return map;
        }
        // 是否激活
        if (user.getStatus() == 0) {
            map.put("usernameMSG", "该账号未激活");
            return map;
        }
        // 验证密码
        password = CommunityUtil.md5(user.getSalt() + password);
        if (!password.equals(user.getPassword())) {
            map.put("passwordMSG", "密码不正确");
            return map;
        }
        // 生成登录凭证
        LoginTicket loginTicket = new LoginTicket();
        loginTicket.setUserId(user.getId());
        loginTicket.setTicket(CommunityUtil.generateUUID());
        loginTicket.setStatus(0);
        loginTicket.setExpired(new Date(System.currentTimeMillis() + expiredSeconds * 1000));
//        loginTicketMapper.insertLoginTicket(loginTicket);

        String ticketKey = RedisKeyUtil.getLoginTicket(loginTicket.getTicket());
        redisTemplate.opsForValue().set(ticketKey, loginTicket);
        map.put("ticket", loginTicket.getTicket());
        return map;

    }

    // 登出业务
    public void logout(String ticket) {
//        loginTicketMapper.updateStatus(ticket, 1);
        String ticketKey = RedisKeyUtil.getLoginTicket(ticket);
        // 取出loginTicket 修改 status = 1 再存放
        LoginTicket loginTicket = (LoginTicket) redisTemplate.opsForValue().get(ticketKey);
        loginTicket.setStatus(1);
        redisTemplate.opsForValue().set(ticketKey, loginTicket);

    }

    // 获取登录凭证
    public LoginTicket findLoginTicket(String ticket) {
        String ticketKey = RedisKeyUtil.getLoginTicket(ticket);
        return (LoginTicket) redisTemplate.opsForValue().get(ticketKey);
//        return loginTicketMapper.selectByTicket(ticket);
    }

    // 更新用户头像地址
    public int updateHeader(int userId, String headerUrl) {
        int rows = userMapper.updateHeader(userId, headerUrl);
        clearCache(userId);
        return rows;
    }

    // 更新密码
    public int updatePassword(int userId, String password) {
        return userMapper.updatePassword(userId, password);
    }

    // 缓存
    // 1 优先从缓存中取值
    private User getCache(int userId) {
        String userKey = RedisKeyUtil.getUserKey(userId);
        return (User) redisTemplate.opsForValue().get(userKey);
    }

    // 2 取不到 初始化缓存数据
    private User initCache(int userId) {
        User user = userMapper.selectById(userId);
        String userKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.opsForValue().set(userKey, user, 3600, TimeUnit.SECONDS);
        return user;
    }

    // 3 数据变更时 清除缓存数据
    private void clearCache(int userId) {
        String userKey = RedisKeyUtil.getUserKey(userId);
        redisTemplate.delete(userKey);
    }

    // 根据用户获取用户权限
    public Collection<? extends GrantedAuthority> getAuthorities(int userId){
        User user = this.findUserById(userId);

        List<GrantedAuthority> list = new ArrayList<>();

        list.add(new GrantedAuthority() {
            @Override
            public String getAuthority() {
                switch (user.getType()){
                    case 1:
                        return AUTHORITY_ADMIN;
                    case 2:
                        return AUTHORITY_MODERATOR;
                    default:
                        return AUTHORITY_USER;
                }
            }
        });
        return list;
    }
}
