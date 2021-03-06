package com.wkk.community.dao;

import com.wkk.community.CommunityApplication;
import com.wkk.community.entity.DiscussPost;
import com.wkk.community.entity.User;
import com.wkk.community.service.DiscussPostService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;
import java.util.List;

/**
 * @Time: 2020/4/29上午8:18
 * @Author: kongwiki
 * @Email: kongwiki@163.com
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = CommunityApplication.class)
class UserMapperTest {

    @Autowired(required = false)
    private UserMapper userMapper;
    @Autowired(required = false)
    private DiscussPostMapper discussPostMapper;

    @Autowired
    private DiscussPostService discussPostService;

    @Test
    public void testSelectUser(){
        User user = userMapper.selectById(101);
        System.out.println(user);

        user = userMapper.selectByName("liubei");
        System.out.println(user);

        user = userMapper.selectByEmail("nowcoder101@sina.com");
        System.out.println(user);
    }

    @Test
    public void testInsertUser(){
        User user = new User();
        user.setUsername("weikunkun");
        user.setPassword("123456");
        user.setSalt("abc");
        user.setEmail("kongwiki@163.com");
        user.setHeaderUrl("http://www.nowcoder.com/101.png");
        user.setCreateTime(new Date());
        int i = userMapper.insertUser(user);
        System.out.println(i);
        System.out.println(userMapper.selectByName("weikunkun"));
    }

    @Test
    public void updateUser(){
        int rows = userMapper.updateStatus(150, 1);
        System.out.println(rows);

        rows = userMapper.updateHeader(150, "http://www.nowcoder.com/150.png");
        System.out.println(rows);
    }

    @Test
    public void testSelectPosts(){
//        List<DiscussPost> discussPosts = discussPostMapper.selectDiscussPosts(0, 0, 10);
//        for (DiscussPost discussPost : discussPosts) {
//            System.out.println("====>" + discussPost);
//        }
        List<DiscussPost> posts = discussPostService.findDiscussPosts(0, 0, 10, 0);
        for (DiscussPost post : posts) {
            System.out.println("+++++++++++++" + post);
        }

    }

    @Test
    public void testSelectById(){
        int id = 281;
        DiscussPost discussPost = discussPostService.findDiscussPostById(id);
        System.out.println(discussPost.getTitle() + " " + discussPost.getContent());

    }

    @Test
    public void testUpdatePost(){
        int id = 281;
        int count = 0;
        int i = discussPostMapper.updateCommentCount(id, count);
        System.out.println(i);
    }
}

