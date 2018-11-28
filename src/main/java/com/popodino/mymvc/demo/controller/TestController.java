package com.popodino.mymvc.demo.controller;

import com.popodino.mymvc.demo.service.TestService1;
import com.popodino.mymvc.framework.annotation.MyAutowired;
import com.popodino.mymvc.framework.annotation.MyController;
import com.popodino.mymvc.framework.annotation.MyRequestMapping;
import com.popodino.mymvc.framework.annotation.MyRequestParam;
import com.popodino.mymvc.framework.servlet.MyModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@MyController
@MyRequestMapping("/test")
public class TestController {

    @MyAutowired private TestService1 service1;

    @MyRequestMapping("/query.*")
    public MyModelAndView query(HttpServletRequest request, HttpServletResponse response,
                                @MyRequestParam("name") String name)throws Exception{

        service1.query(name);
        MyModelAndView modelAndView = new MyModelAndView();

        modelAndView.setView("name.po");
        Map<String,Object> model = new HashMap<String, Object>();
        model.put("name",name);
        modelAndView.setModel(model);
        return modelAndView;
    }
}
