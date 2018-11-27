package com.popodino.mymvc.demo.controller;

import com.popodino.mymvc.demo.service.TestService1;
import com.popodino.mymvc.framework.annotation.MyAutowired;
import com.popodino.mymvc.framework.annotation.MyController;
import com.popodino.mymvc.framework.annotation.MyRequestMapping;
import com.popodino.mymvc.framework.annotation.MyRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@MyController
@MyRequestMapping("/test")
public class TestController {

    @MyAutowired private TestService1 service1;

    @MyRequestMapping("/query")
    public void query(HttpServletRequest request, HttpServletResponse response,
                      @MyRequestParam("name") String name)throws Exception{
        response.getWriter().write("My name is " + name);
        service1.query(name);
    }
}
