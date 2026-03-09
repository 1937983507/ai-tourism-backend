# 批量将csv的景点数据插入到数据库

import pandas as pd
import pymysql
from sqlalchemy import create_engine, text
from tqdm import tqdm
import os
import json
import time

# ========================================
# 1. 数据库配置
# ========================================
DB_CONFIG = {
    "host": "localhost",
    "port": 3306,
    "user": "root",
    "password": "123456",  # 修改为你的密码
    "database": "aitourism",   # 修改为你的数据库名
    "charset": "utf8mb4"
}

# SQLAlchemy 连接字符串
CONN_STR = (
    f"mysql+pymysql://{DB_CONFIG['user']}:{DB_CONFIG['password']}"
    f"@{DB_CONFIG['host']}:{DB_CONFIG['port']}/{DB_CONFIG['database']}?charset={DB_CONFIG['charset']}"
)
engine = create_engine(CONN_STR)

# ========================================
# 2. 文件路径配置
# ========================================
CSV_FILE = "poi.csv"
LOG_FILE = "failed_rows.log"
STATE_FILE = "import_state.json"  # 用于断点续传

# ========================================
# 3. 函数：建表（如果不存在）
# ========================================
CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS t_poi (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    poi_name VARCHAR(255) NOT NULL COMMENT '景点名称',
    city_name VARCHAR(255) NOT NULL COMMENT '城市名称',
    poi_description TEXT NOT NULL COMMENT '景点描述',
    poi_longitude FLOAT NOT NULL COMMENT '经度',
    poi_latitude FLOAT NOT NULL COMMENT '纬度',
    poi_image_url VARCHAR(512) NOT NULL COMMENT '景点图片链接',
    poi_rankInCity INT NOT NULL COMMENT '城市内排名',
    poi_rankInChina INT NOT NULL COMMENT '全国排名',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    modify_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP
) COMMENT '景点信息表';
"""

# ========================================
# 4. 加载断点进度
# ========================================
def load_state():
    if os.path.exists(STATE_FILE):
        with open(STATE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"last_index": -1}

# ========================================
# 5. 保存断点进度
# ========================================
def save_state(index):
    with open(STATE_FILE, "w", encoding="utf-8") as f:
        json.dump({"last_index": index}, f)

# ========================================
# 6. 主流程
# ========================================
def main():
    print("🚀 正在连接数据库...")
    with engine.connect() as conn:
        conn.execute(text(CREATE_TABLE_SQL))
        print("✅ 数据表 t_poi 已确认存在。")

    print(f"📂 正在读取 {CSV_FILE} ...")
    try:
        df = pd.read_csv(CSV_FILE, encoding="utf-8-sig")
    except UnicodeDecodeError:
        df = pd.read_csv(CSV_FILE, encoding="gbk")

    # 字段映射
    df = df.rename(columns={
        "pname": "poi_name",
        "city": "city_name",
        "X_gcj02": "poi_longitude",
        "Y_gcj02": "poi_latitude",
        "rankInCity": "poi_rankInCity",
        "rankInChina": "poi_rankInChina",
        "intro": "poi_description",
        "image_url": "poi_image_url"
    })

    df["poi_description"] = df["poi_description"].fillna("")
    df["city_name"] = df["city_name"].fillna("未知城市")

    # 加载断点
    state = load_state()
    start_index = state["last_index"] + 1
    total = len(df)

    print(f"🔁 从第 {start_index} 条开始导入，共 {total} 条")

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    # 进度条
    for i in tqdm(range(start_index, total), desc="导入进度"):
        row = df.iloc[i]
        try:
            cursor.execute("""
                INSERT INTO t_poi (poi_name, city_name, poi_description, poi_longitude, poi_latitude, poi_image_url, poi_rankInCity, poi_rankInChina)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            """, (
                row["poi_name"], row["city_name"], row["poi_description"],
                float(row["poi_longitude"]), float(row["poi_latitude"]), row["poi_image_url"],
                int(row["poi_rankInCity"]), int(row["poi_rankInChina"])
            ))
            if i % 500 == 0:
                conn.commit()
                save_state(i)
        except Exception as e:
            with open(LOG_FILE, "a", encoding="utf-8") as log:
                log.write(f"行 {i} 插入失败：{row.to_dict()}，错误：{e}\n")
            conn.rollback()

    conn.commit()
    conn.close()
    save_state(total - 1)
    print("✅ 全部数据导入完成！")
    print(f"📘 错误日志文件：{LOG_FILE}")
    print(f"💾 断点文件：{STATE_FILE}")

# ========================================
if __name__ == "__main__":
    main()
