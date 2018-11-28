package com.popodino.mymvc.framework.servlet;

import com.popodino.mymvc.framework.annotation.MyController;
import com.popodino.mymvc.framework.annotation.MyRequestMapping;
import com.popodino.mymvc.framework.annotation.MyRequestParam;
import com.popodino.mymvc.framework.context.MyApplicationContext;
import com.sun.org.glassfish.gmbal.ParameterNames;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模仿Spring的DispatcherServlet
 * @author popodino
 */
public class MyDispatcherServlet extends HttpServlet {

    /**
     * Spring配置文件的名称
     */
    private static final String LOCATION = "contextConfigLocation";

    private List<Handler> handlerMapping = new ArrayList<Handler>();

    private List<HandlerAdapter> adapterMapping = new ArrayList<HandlerAdapter>();

    private List<ViewResolver> viewResolvers = new ArrayList<ViewResolver>();

    /**
     *初始化MyMVC
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        //获得IOC容器
        MyApplicationContext context = new MyApplicationContext(config.getInitParameter(LOCATION));

        //初始化url与method的对应关系
        initHandlerMappings(context);

        //初始化method中的参数列表
        initHandlerAdapters(context);

        //初始化视图解析器，保存配置的解析器的信息
        initViewResolvers(context);


    }


    private void initHandlerMappings(MyApplicationContext context){
        Map<String,Object> ioc = context.getAll();
        if(ioc.isEmpty()){ return;}

        //遍历IOC中Controller的method，拼凑Url
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
                //如果MyRequestMapping中有多个/,替换成一个
                String regex = url + myRequestMapping.value().replaceAll("/+","/");
                //MyRequestMapping支持正则表达式
                Pattern pattern = Pattern.compile(regex);

                handlerMapping.add(new Handler(pattern,entry.getValue(),method));
            }
        }
    }

    private void initHandlerAdapters(MyApplicationContext context){
        if(handlerMapping.isEmpty()){return;}

        for (Handler handler : handlerMapping) {
            //Jdk1.8以前，只能获得参数的类型，不能获得参数名称，1.8后可以加@ParameterNames注解
            //Spring采用ASM解析字节码获得参数名称
            Class<?> [] paramsTypes = handler.method.getParameterTypes();
            //String存参数名或参数类型名，Integer存参数索引号
            Map<String,Integer> paramMapping = new HashMap<String, Integer>();
            for (int i = 0; i < paramsTypes.length ; i++) {
                Class<?> type = paramsTypes[i];
                if(type == HttpServletRequest.class || type == HttpServletResponse.class){
                    paramMapping.put(type.getName(),i);
                }
            }

            Annotation [][] pa = handler.method.getParameterAnnotations();
            for (int i = 0; i < pa.length; i++) {
                for (Annotation a : pa[i]) {
                    if(a instanceof MyRequestParam){
                        String paramName = ((MyRequestParam) a).value();
                        if(!"".equals(paramName.trim())){
                            paramMapping.put(paramName,i);
                        }
                    }
                }
            }
            adapterMapping.add(new HandlerAdapter(handler,paramMapping));
        }
    }

    private void initViewResolvers(MyApplicationContext context){

        String templateRoot = context.getConfig().getProperty("templateRoot");

        String rootPath = this.getClass().getClassLoader().getResource(templateRoot).getFile();
        File dir = new File(rootPath);
        for (File file : dir.listFiles()) {
            viewResolvers.add(new ViewResolver(file.getName(),file));
        }

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

    /**
     * 访问时调用的是doDispatcher方法，获取handler，
     * 然后获得adapter，执行adapter的handle方法，返回ModelAndView，交给视图解析器解析
     */
    private void doDispatcher(HttpServletRequest request, HttpServletResponse response) throws Exception{

        Handler handler = getHandler(request);
        if(null == handler){
            response.getWriter().write("404 not found");
            return ;
        }

        HandlerAdapter ha = getHandlerAdapter(handler);

        MyModelAndView mv = ha.handle(request,response,handler);

        applyDefaultViewName(response,mv);
    }

    private Handler getHandler(HttpServletRequest request){
        if(handlerMapping.isEmpty()){ return null;}

        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        //去掉请求地址中多余的/，通过正则与handler中的url匹配
        url = url.replace(contextPath,"").replaceAll("/+","/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(url);
            if(!matcher.matches()){continue;}

            return handler;
        }
        return null;
    }

    private HandlerAdapter getHandlerAdapter(Handler handler){
        if(adapterMapping.isEmpty()){return null;}
        //根据Handler获得一个Adapter
        for (HandlerAdapter adapter : adapterMapping) {
            if(adapter.handler == handler){
                return adapter;
            }
        }
        return null;
    }

    private void applyDefaultViewName(HttpServletResponse response,MyModelAndView mv) throws Exception{
        if(null == mv){return;}
        if(viewResolvers.isEmpty()){return;}

        for (ViewResolver resolver : viewResolvers) {
            if(!mv.getView().equals(resolver.getViewName())){continue;}

            //获得匹配的视图解析器，调用解析器的parse解析方法
            String r = resolver.parse(mv);
            if(!"".equals(r)){
                response.getWriter().write(r);
                break;
            }
        }
    }

    private class Handler{
        private Object controller;
        private Method method;
        private Pattern pattern;

        private Handler(Pattern pattern, Object controller, Method method){
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
        }

    }

    private class HandlerAdapter{
        private Handler handler;
        private Map<String,Integer> paramMapping;

        private HandlerAdapter(Handler handler, Map<String,Integer> paramMapping){
            this.handler = handler;
            this.paramMapping = paramMapping;
        }

        /**
         * 通过反射调用url对应的method
         */
        private MyModelAndView handle(HttpServletRequest request, HttpServletResponse response, Handler handler)throws Exception{
            Class<?> [] paramTypes =  handler.method.getParameterTypes();
            Object [] paramValues = new Object[paramTypes.length];
            Map<String,String []> params = request.getParameterMap();

            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                //如果参数有多个值，则以，分隔
                String value = Arrays.toString(entry.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s",",");
                if(!paramMapping.containsKey(entry.getKey())){continue;}

                int index = this.paramMapping.get(entry.getKey());
                paramValues[index] = castStringValue(value,paramTypes[index]);
            }

            //设置request，response参数值
            if(this.paramMapping.containsKey(HttpServletRequest.class.getName())){
                int reqIndex = this.paramMapping.get(HttpServletRequest.class.getName());
                paramValues[reqIndex] = request;
            }
            if (this.paramMapping.containsKey(HttpServletResponse.class.getName())) {
                int resIndex = this.paramMapping.get(HttpServletResponse.class.getName());
                paramValues[resIndex] = response;
            }

            boolean isModelAndView = handler.method.getReturnType() == MyModelAndView.class;
            //反射调用method
            Object r = handler.method.invoke(handler.controller,paramValues);
            if(isModelAndView){
                return (MyModelAndView)r;
            }else {
                return null;
            }

        }

        /**
         * 参数类型转换器
         */
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

    /**
     * 自定义视图解析器，这里的匹配方式是 #...#
     */
    private class ViewResolver{
        private String viewName;
        private File file;
        private Pattern pattern = Pattern.compile("\\#(.+?)\\#",Pattern.CASE_INSENSITIVE);

        private ViewResolver(String viewName,File file){
            this.viewName = viewName;
            this.file = file;
        }

        /**
         * 真正解析的方法
         */
        private String parse(MyModelAndView mv) throws Exception{
            StringBuffer sb = new StringBuffer();
            RandomAccessFile ra = new RandomAccessFile(this.file,"r");
            try {
                String line = "";
                while (null !=(line = ra.readLine())){
                    Matcher m = matcher(line);
                    while (m.find()){
                        for (int i = 1; i <= m.groupCount(); i++) {
                            String paramName = m.group(i);
                            Object paramValue = mv.getModel().get(paramName);
                            if(null == paramValue){continue;}
                            line = line.replaceAll("\\#"+ paramName +"\\#",paramValue.toString());
                        }
                    }
                    sb.append(line);
                }
            } finally {
                ra.close();
            }
            return sb.toString();
        }

        private Matcher matcher(String str){
            Matcher m = pattern.matcher(str);
            return m;
        }

        private String getViewName() {
            return viewName;
        }
    }

}
