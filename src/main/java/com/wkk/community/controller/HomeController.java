package com.wkk.community.controller;

import com.wkk.community.entity.DiscussPost;
import com.wkk.community.entity.User;
import com.wkk.community.service.DiscussPostService;
import com.wkk.community.service.LikeService;
import com.wkk.community.service.UserService;
import com.wkk.community.util.CommunityConstant;
import com.wkk.community.util.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Time: 2020/4/29上午11:14
 * @Author: kongwiki
 * @Email: kongwiki@163.com
 */
@Controller
public class HomeController implements CommunityConstant {
    @Autowired
    private DiscussPostService discussPostService;
    @Autowired
    private UserService userService;
    @Autowired
    private LikeService likeService;

    @RequestMapping(value = "/index", method = RequestMethod.GET)
    public String getIndexPage(Model model, Page page,
                               @RequestParam(name = "orderMode", defaultValue = "0") int orderMode) {
        page.setPath("/index?orderMode=" + orderMode);
        page.setRows(discussPostService.findDiscussPostRows(0));
        List<DiscussPost> discussPosts = discussPostService.
                findDiscussPosts(0, page.getOffset(), page.getLimit(), orderMode);
        List<Map<String, Object>> list = new ArrayList<>();
        if (discussPosts != null) {
            for (DiscussPost discussPost : discussPosts) {
                Map<String, Object> map = new HashMap<>();
                map.put("post", discussPost);
                User user = userService.findUserById(discussPost.getUserId());
                map.put("user", user);
                long likeCount = likeService.findEntityCount(discussPost.getId(), ENTITY_TYPE_POST);
                map.put("likeCount", likeCount);
                list.add(map);
            }
        }
        model.addAttribute("discussPosts", list);
        model.addAttribute("orderMode", orderMode);
        return "/index";
    }

    @RequestMapping(value = "/error", method = RequestMethod.GET)
    public String getErrorPage() {
        return "error/500";
    }

    // 没有权限
    @RequestMapping(value = "/denied", method = RequestMethod.GET)
    public String getDenied() {
        return "/error/404";
    }
}
