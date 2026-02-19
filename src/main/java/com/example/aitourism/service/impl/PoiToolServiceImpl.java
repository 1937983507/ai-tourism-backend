package com.example.aitourism.service.impl;

import com.example.aitourism.entity.POI;
import com.example.aitourism.mapper.POIMapper;
import com.example.aitourism.service.PoiToolService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * POI 工具服务实现
 */
@Service
@Slf4j
public class PoiToolServiceImpl implements PoiToolService {

    private final POIMapper poiMapper;

    public PoiToolServiceImpl(POIMapper poiMapper) {
        this.poiMapper = poiMapper;
    }

    @Override
    public List<POI> listByCityName(String cityName, Integer limit) {
        if (cityName == null || cityName.trim().isEmpty()) {
            throw new IllegalArgumentException("城市名称不能为空");
        }
        if (limit == null || limit <= 0) {
            limit = 10;
        }
        String normalizedCity = cityName.trim();
        log.info("查询POI数据，城市: {}, 数量: {}", normalizedCity, limit);
        return poiMapper.findByCityName(normalizedCity, limit);
    }
}


