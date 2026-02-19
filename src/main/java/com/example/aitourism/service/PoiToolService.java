package com.example.aitourism.service;

import com.example.aitourism.entity.POI;

import java.util.List;

/**
 * POI 工具服务
 * 为 Python Agent 的工具调用提供 POI 查询能力
 */
public interface PoiToolService {

    /**
     * 根据城市名称查询 POI 列表
     *
     * @param cityName 城市名称
     * @param limit 返回数量上限
     * @return POI 列表
     */
    List<POI> listByCityName(String cityName, Integer limit);
}


