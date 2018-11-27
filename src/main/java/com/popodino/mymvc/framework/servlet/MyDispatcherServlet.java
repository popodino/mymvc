package com.popodino.mymvc.framework.servlet;

import com.popodino.mymvc.framework.annotation.MyController;
import com.popodino.mymvc.framework.annotation.MyRequestMapping;
import com.popodino.mymvc.framework.annotation.MyRequestParam;
import com.popodino.mymvc.framework.context.MyApplicationContext;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MyDispatcherServlet extends HttpServlet {

    private static final String LOCATION = "contextConfigLocation";

    private Map<String,Handler> handlerMapping = new HashMap<String, Handler>();

    private Map<Handler,HandlerAdapter> adapterMapping = new HashMap<Handler, HandlerAdapter>();

    /**
     *初始化MyMVC
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        MyApplicationContext context = new MyApplicationContext(config.getInitParameter(LOCATION));

        initHandlerMappings(context);

        initHandlerAdapters(context);

        initViewResolvers(context);


    }

    private void initHandlerMappings(MyApplicationContext context){
        Map<String,Object> ioc = context.getAll();
        if(ioc.isEmpty()){ return;}

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if(!clazz.isAnnotationPresent(MyController.class)){ continue;}

            String url = "";
            if(clazz.isAnnotationPresent(MyRequestMapping.class)){
                MyRequestMapping myRequestMapping = clazz.getAnnotation(MyRequestMapping.class);
                url = myRequestMapping.value();
            }

            Method [] methods = clazz.getMethods();

            for (Method method : methods) {
                if(!method.isAnnotationPresent(MyRequestMapping.class)){continue;}
                MyRequestMapping myRequestMapping = method.getAnnotation(MyRequestMapping.class);
                String mappingUrl = url + myRequestMapping.value();
                handlerMapping.put(mappingUrl,new Handler(entry.getValue(),method));
            }
        }
    }

    private void initHandlerAdapters(MyApplicationContext context){
        if(handlerMapping.isEmpty()){return;}
        for (Map.Entry<String, Handler> entry : handlerMapping.entrySet()) {
            Class<?> [] paramsTypes = entry.getValue().method.getParameterTypes();
            String[] paramMapping = new String[paramsTypes.length];
            for (int i = 0; i < paramsTypes.length ; i++) {
                if(paramsTypes[i].isAnnotationPresent(MyRequestParam.class)){
                    MyRequestParam myRequestParam = paramsTypes[i].getAnnotation(MyRequestParam.class);
                    String paramName = myRequestParam.value();
                    if("".equals(paramName.trim())){
                        paramName = paramsTypes[i].getName();
                    }
                    paramMapping[i] = paramName;
                }else {
                    paramMapping[i] = paramsTypes[i].getName();
                }
            }
            adapterMapping.put(entry.getValue(),new HandlerAdapter(paramMapping));
        }
    }

    private void initViewResolvers(MyApplicationContext context){

    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatcher(req,resp);
        }catch (Exception e){
            resp.getWriter().write("500 Exception, Msg :" + Arrays.toString(e.getStackTrace()));
        }
    }

    private void doDispatcher(HttpServletRequest request, HttpServletResponse response) throws Exception{

        Handler handler = getHandler(request);
        if(null == handler){
            response.getWriter().write("404 not found");
            return ;
        }

        HandlerAdapter ha = getHandlerAdapter(handler);

        ha.handle(request,response,handler);
    }

    private Handler getHandler(HttpServletRequest request){
        if(handlerMapping.isEmpty()){ return null;}

        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+","/");

        for (Map.Entry<String, Handler> entry : handlerMapping.entrySet()) {
            if(url.equals(entry.getKey())){
                return entry.getValue();
            }
        }
        return null;
    }

    private HandlerAdapter getHandlerAdapter(Handler handler){
        if(adapterMapping.isEmpty()){return null;}
        return adapterMapping.get(handler);
    }

    private class Handler{
        private Object controller;
        private Method method;

        private Handler(Object controller, Method method){
            this.controller = controller;
            this.method = method;
        }
        public Object getController() {
            return controller;
        }

        public void setController(Object controller) {
            this.controller = controller;
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }
    }

    private class HandlerAdapter{
        private String [] paramMapping;

        public HandlerAdapter(String [] paramMapping){
            this.paramMapping = paramMapping;
        }
        public void handle(HttpServletRequest request, HttpServletResponse response, Handler handler)throws Exception{
            Class<?> [] paramTypes =  handler.method.getParameterTypes();
            Object [] paramValues = new Object[paramTypes.length];
            Map<String,String []> params = request.getParameterMap();
            for (int i = 0; i < paramMapping.length; i++) {
                if(paramTypes[i] == HttpServletRequest.class){
                    paramValues[i] = request;
                }else if (paramTypes[i] == HttpServletResponse.class){
                    paramValues[i] = response;
                }else {
                    for (Map.Entry<String, String[]> entry : params.entrySet()) {
                        String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s",",");
                        if (paramMapping[i] == entry.getKey()){
                            paramValues[i] = castStringValue(value,paramTypes[i]);
                        }
                    }
                }
            }

            handler.method.invoke(handler.controller,paramValues);

        }

        private Object castStringValue(String value,Class<?> clazz){
            if(clazz == String.class){
                return value;
            }else if(clazz == int.class || clazz == Integer.class){
                return Integer.valueOf(value);
            }else {
                return null;
            }
        }
    }

}
