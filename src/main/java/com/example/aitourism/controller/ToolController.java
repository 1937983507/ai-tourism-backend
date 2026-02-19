package com.example.aitourism.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.example.aitourism.dto.BaseResponse;
import com.example.aitourism.entity.POI;
import com.example.aitourism.service.PoiToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工具接口控制器
 * 为 Python Agent 提供只读业务数据接口
 */
@RestController
@RequestMapping("/api/tools")
@Slf4j
public class ToolController {

    private final PoiToolService poiToolService;
    
    @Value("${agent.internal-token:}")
    private String internalToken;

    public ToolController(PoiToolService poiToolService) {
        this.poiToolService = poiToolService;
    }

    /**
     * POI 查询接口
     * 供 Python Agent 的 poi_search 工具调用
     * 
     * @param cityName 城市名称
     * @param count 返回的景点数量，默认10
     * @param authorization 内部认证token（可选）
     * @return POI列表
     */
    @SaIgnore
    @GetMapping("/poi")
    public BaseResponse<List<POI>> getPOI(
            @RequestParam("city_name") String cityName,
            @RequestParam(value = "count", defaultValue = "10") Integer count,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        // 验证内部token（如果配置了）
        if (internalToken != null && !internalToken.isEmpty()) {
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                log.warn("POI接口调用缺少Authorization头");
                return BaseResponse.error(401, "Unauthorized");
            }
            String token = authorization.substring(7);
            if (!token.equals(internalToken)) {
                log.warn("POI接口调用token验证失败");
                return BaseResponse.error(401, "Unauthorized");
            }
        }
        
        try {
            List<POI> poiList = poiToolService.listByCityName(cityName, count);
            log.info("查询到 {} 条POI数据", poiList.size());
            
            return BaseResponse.success(poiList);
        } catch (IllegalArgumentException e) {
            return BaseResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("查询POI数据异常: {}", e.getMessage(), e);
            return BaseResponse.error(500, "查询POI数据失败: " + e.getMessage());
        }
    }
}

