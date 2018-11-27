package com.popodino.mymvc.framework.context;

import com.popodino.mymvc.framework.annotation.MyAutowired;
import com.popodino.mymvc.framework.annotation.MyCompotent;
import com.popodino.mymvc.framework.annotation.MyController;
import com.popodino.mymvc.framework.annotation.MyService;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public class MyApplicationContext {

    private Map<String,Object> instanceMapping = new ConcurrentHashMap<String, Object>();
    private List<String> beanDefinition = new ArrayList<String>();

    public MyApplicationContext(String location){

        InputStream is = null;

        try{
            is = this.getClass().getClassLoader().getResourceAsStream(location);

            Properties config = new Properties();
            config.load(is);
            String packageName = config.getProperty("scanPackage");

            doRegister(packageName);

            doCreateBean();

            populate();

        }catch (Exception e){
            e.printStackTrace();
        }
        System.out.println("IOC容器已经初始化");
    }

    public Object getBean(String beanName){
        return instanceMapping.get(beanName);
    }

    public Map<String,Object> getAll(){
        return instanceMapping;
    }

    private void doRegister(String packageName){
        URL url =  this.getClass().getClassLoader().getResource("/" + packageName.replaceAll("\\.","/"));
        File dir = new File(url.getFile());

        for (File file: dir.listFiles()) {
        if(file.isDirectory()){
            doRegister(packageName + "." + file.getName());
        }else {
            beanDefinition.add(packageName + "." + file.getName().replaceAll(".class","").trim());
            }
        }
    }

    private void doCreateBean(){
        if(beanDefinition.size() == 0){ return;}

        try {
            for (String className : beanDefinition) {
                Class<?> clazz = Class.forName(className);

                if (clazz.isAnnotationPresent(MyController.class)){
                    String id = lowerFirstChar(clazz.getSimpleName());
                    instanceMapping.put(id,clazz.newInstance());

                }else if (clazz.isAnnotationPresent(MyCompotent.class)){
                    MyCompotent myCompotent = clazz.getAnnotation(MyCompotent.class);
                    String id = myCompotent.value();
                    if ("".equals(id.trim())){
                        id = lowerFirstChar(clazz.getSimpleName());
                    }
                    instanceMapping.put(id,clazz.newInstance());

                }else if (clazz.isAnnotationPresent(MyService.class)){
                    MyService myService = clazz.getAnnotation(MyService.class);
                    String id = myService.value();
                    Object instance = clazz.newInstance();
                    if ("".equals(id.trim())){
                        id = lowerFirstChar(clazz.getSimpleName());
                    }
                    instanceMapping.put(id,instance);
                    Class<?> [] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces) {
                        if(instanceMapping.containsKey(i.getName())){
                            throw new Exception("The beanName: " + i.getName() + " is exists");
                        }
                        instanceMapping.put(i.getName(),instance);
                    }
                }else {
                    continue;
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void populate(){
        if(instanceMapping.isEmpty()){ return;}

        for (Map.Entry<String,Object> entry : instanceMapping.entrySet()) {
            Field [] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields) {
                if(!field.isAnnotationPresent(MyAutowired.class)){ continue;};

                MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
                String id = myAutowired.value().trim();
                if("".equals(id)){
                    id = field.getType().getName();
                }
                field.setAccessible(true);

                try {
                    field.set(entry.getValue(),instanceMapping.get(id));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;
                }
            }
        }
    }

    private String lowerFirstChar(String str) {
        char [] chars = str.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
 }
