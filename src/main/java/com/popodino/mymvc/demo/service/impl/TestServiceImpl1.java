package com.popodino.mymvc.demo.service.impl;

import com.popodino.mymvc.demo.service.TestService1;
import com.popodino.mymvc.framework.annotation.MyService;

@MyService("queryService")
public class TestServiceImpl1 implements TestService1 {

    public void query(String name) {
        System.out.println("My name is " + name);
    }
}
