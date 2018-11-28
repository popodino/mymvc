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

/**
 * 模仿Spring的ApplicationContext
 * @author popodino
 */
public class MyApplicationContext {

    /**
     * IOC容器，就是一个ConcurrentHashMap
     */
    private Map<String,Object> instanceMapping = new ConcurrentHashMap<String, Object>();
    private List<String> beanDefinition = new ArrayList<String>();
    private Properties config = new Properties();

    /**
     * IOC初始化分为5步：定位、加载、注册、初始化、注入
     */
    public MyApplicationContext(String location){

        InputStream is = null;
        try{
            //定位
            is = this.getClass().getClassLoader().getResourceAsStream(location);
            //载入
            config.load(is);
            String packageName = config.getProperty("scanPackage");

            //注册
            doRegister(packageName);

            //初始化
            doCreateBean();

            //依赖注入
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

    /**
     * 将所定位下的所有类的信息，注册为BeanDefinition，保存了类的基本信息和依赖信息
     */
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

    /**
     * 实例化需要被Spring托管的类，Spring中是通过动态代理的实例化Bean，为了获取Bean所有权限
     */
    private void doCreateBean(){
        if(beanDefinition.size() == 0){ return;}
        try {
            for (String className : beanDefinition) {
                Class<?> clazz = Class.forName(className);

                    //实例化Controller
                if (clazz.isAnnotationPresent(MyController.class)){
                    String id = lowerFirstChar(clazz.getSimpleName());
                    instanceMapping.put(id,clazz.newInstance());

                    //实例化加了@Compontent的类
                }else if (clazz.isAnnotationPresent(MyCompotent.class)){
                    MyCompotent myCompotent = clazz.getAnnotation(MyCompotent.class);
                    String id = myCompotent.value();
                    if ("".equals(id.trim())){
                        id = lowerFirstChar(clazz.getSimpleName());
                    }
                    instanceMapping.put(id,clazz.newInstance());

                    //实例化Service
                }else if (clazz.isAnnotationPresent(MyService.class)){
                    MyService myService = clazz.getAnnotation(MyService.class);
                    //自命名，否者默认首字母小写
                    String id = myService.value();
                    Object instance = clazz.newInstance();
                    if ("".equals(id.trim())){
                        id = lowerFirstChar(clazz.getSimpleName());
                    }
                    instanceMapping.put(id,instance);

                    //service的接口，以接口全名为key，service的实例为值
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

    /**
     * 依赖注入，加了@Autowired的才注入
     */
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
                //开启暴力访问，private也照样注入
                field.setAccessible(true);

                try {
                    //给字段赋值（注入）
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

    public Properties getConfig() {
        return config;
    }
}
