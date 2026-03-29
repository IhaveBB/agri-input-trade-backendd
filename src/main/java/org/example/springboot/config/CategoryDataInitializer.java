package org.example.springboot.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.springboot.entity.Category;
import org.example.springboot.mapper.CategoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类数据初始化器 - 系统启动时初始化预置分类数据
 */
@Component
public class CategoryDataInitializer implements CommandLineRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryDataInitializer.class);

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    @Transactional
    public void run(String... args) {
        // 检查是否已有分类数据
        Long count = categoryMapper.selectCount(new LambdaQueryWrapper<>());
        if (count > 0) {
            LOGGER.info("分类数据已存在，跳过初始化");
            return;
        }

        LOGGER.info("开始初始化分类数据...");
        List<Category> categories = buildDefaultCategories();
        for (Category category : categories) {
            categoryMapper.insert(category);
        }
        LOGGER.info("分类数据初始化完成，共 {} 条", categories.size());
    }

    /**
     * 构建默认分类数据
     */
    private List<Category> buildDefaultCategories() {
        List<Category> categories = new ArrayList<>();
        long id = 1;

        // ===== 一级分类 =====
        // 1. 种子
        Category seeds = createCategory(id++, "种子", 0L, 1, 1, 0, 0);
        categories.add(seeds);

        // 2. 农药
        Category pesticide = createCategory(id++, "农药", 0L, 1, 2, 0, 0);
        categories.add(pesticide);

        // 3. 肥料
        Category fertilizer = createCategory(id++, "肥料", 0L, 1, 3, 0, 0);
        categories.add(fertilizer);

        // 4. 饲料
        Category feed = createCategory(id++, "饲料", 0L, 1, 4, 0, 0);
        categories.add(feed);

        // 5. 兽药
        Category veterinary = createCategory(id++, "兽药", 0L, 1, 5, 0, 0);
        categories.add(veterinary);

        // 6. 农膜
        Category film = createCategory(id++, "农膜", 0L, 1, 6, 0, 0);
        categories.add(film);

        // 7. 农机
        Category machinery = createCategory(id++, "农机", 0L, 1, 7, 0, 0);
        categories.add(machinery);

        // ===== 二级分类 - 种子 =====
        // 种子 -> 粮食种子
        Category grainSeeds = createCategory(id++, "粮食种子", seeds.getId(), 2, 1, 0, 0);
        categories.add(grainSeeds);
        // 种子 -> 蔬菜种子
        Category vegetableSeeds = createCategory(id++, "蔬菜种子", seeds.getId(), 2, 2, 0, 0);
        categories.add(vegetableSeeds);
        // 种子 -> 水果种子
        Category fruitSeeds = createCategory(id++, "水果种子", seeds.getId(), 2, 3, 0, 0);
        categories.add(fruitSeeds);
        // 种子 -> 花卉种子
        Category flowerSeeds = createCategory(id++, "花卉种子", seeds.getId(), 2, 4, 0, 0);
        categories.add(flowerSeeds);
        // 种子 -> 林木种子
        Category treeSeeds = createCategory(id++, "林木种子", seeds.getId(), 2, 5, 0, 0);
        categories.add(treeSeeds);
        // 种子 -> 中药材种子
        Category herbalSeeds = createCategory(id++, "中药材种子", seeds.getId(), 2, 6, 0, 0);
        categories.add(herbalSeeds);

        // ===== 二级分类 - 农药 =====
        // 农药 -> 杀虫剂
        Category insecticide = createCategory(id++, "杀虫剂", pesticide.getId(), 2, 1, 0, 0);
        categories.add(insecticide);
        // 农药 -> 杀螨剂
        Category acaricide = createCategory(id++, "杀螨剂", pesticide.getId(), 2, 2, 0, 0);
        categories.add(acaricide);
        // 农药 -> 杀菌剂
        Category fungicide = createCategory(id++, "杀菌剂", pesticide.getId(), 2, 3, 0, 0);
        categories.add(fungicide);
        // 农药 -> 除草剂
        Category herbicide = createCategory(id++, "除草剂", pesticide.getId(), 2, 4, 0, 0);
        categories.add(herbicide);
        // 农药 -> 植物生长调节剂
        Category regulator = createCategory(id++, "植物生长调节剂", pesticide.getId(), 2, 5, 0, 0);
        categories.add(regulator);
        // 农药 -> 杀线虫剂
        Category nematocide = createCategory(id++, "杀线虫剂", pesticide.getId(), 2, 6, 0, 0);
        categories.add(nematocide);
        // 农药 -> 杀鼠剂
        Category rodenticide = createCategory(id++, "杀鼠剂", pesticide.getId(), 2, 7, 0, 0);
        categories.add(rodenticide);

        // ===== 二级分类 - 肥料 =====
        // 肥料 -> 单质肥料
        Category singleFertilizer = createCategory(id++, "单质肥料", fertilizer.getId(), 2, 1, 0, 0);
        categories.add(singleFertilizer);
        // 肥料 -> 复合肥料
        Category compoundFertilizer = createCategory(id++, "复合肥料", fertilizer.getId(), 2, 2, 0, 0);
        categories.add(compoundFertilizer);
        // 肥料 -> 有机肥料
        Category organicFertilizer = createCategory(id++, "有机肥料", fertilizer.getId(), 2, 3, 0, 0);
        categories.add(organicFertilizer);
        // 肥料 -> 生物肥料
        Category bioFertilizer = createCategory(id++, "生物肥料", fertilizer.getId(), 2, 4, 0, 0);
        categories.add(bioFertilizer);
        // 肥料 -> 微量元素肥料
        Category microElement = createCategory(id++, "微量元素肥料", fertilizer.getId(), 2, 5, 0, 0);
        categories.add(microElement);
        // 肥料 -> 水溶肥料
        Category waterSoluble = createCategory(id++, "水溶肥料", fertilizer.getId(), 2, 6, 0, 0);
        categories.add(waterSoluble);

        // ===== 二级分类 - 饲料 =====
        // 饲料 -> 粗饲料
        Category roughage = createCategory(id++, "粗饲料", feed.getId(), 2, 1, 0, 0);
        categories.add(roughage);
        // 饲料 -> 青绿饲料
        Category greenFeed = createCategory(id++, "青绿饲料", feed.getId(), 2, 2, 0, 0);
        categories.add(greenFeed);
        // 饲料 -> 青贮饲料
        Category silage = createCategory(id++, "青贮饲料", feed.getId(), 2, 3, 0, 0);
        categories.add(silage);
        // 饲料 -> 能量饲料
        Category energyFeed = createCategory(id++, "能量饲料", feed.getId(), 2, 4, 0, 0);
        categories.add(energyFeed);
        // 饲料 -> 蛋白质补充料
        Category proteinSupplement = createCategory(id++, "蛋白质补充料", feed.getId(), 2, 5, 0, 0);
        categories.add(proteinSupplement);
        // 饲料 -> 矿物质饲料
        Category mineralFeed = createCategory(id++, "矿物质饲料", feed.getId(), 2, 6, 0, 0);
        categories.add(mineralFeed);
        // 饲料 -> 维生素饲料
        Category vitaminFeed = createCategory(id++, "维生素饲料", feed.getId(), 2, 7, 0, 0);
        categories.add(vitaminFeed);

        // ===== 二级分类 - 兽药 =====
        // 兽药 -> 抗菌药物
        Category antibacterial = createCategory(id++, "抗菌药物", veterinary.getId(), 2, 1, 0, 0);
        categories.add(antibacterial);
        // 兽药 -> 抗病毒药物
        Category antiviral = createCategory(id++, "抗病毒药物", veterinary.getId(), 2, 2, 0, 0);
        categories.add(antiviral);
        // 兽药 -> 抗寄生虫药物
        Category antiparasitic = createCategory(id++, "抗寄生虫药物", veterinary.getId(), 2, 3, 0, 0);
        categories.add(antiparasitic);

        // ===== 二级分类 - 农膜 =====
        // 农膜 -> 地膜
        Category groundFilm = createCategory(id++, "地膜", film.getId(), 2, 1, 0, 0);
        categories.add(groundFilm);
        // 农膜 -> 棚膜
        Category greenhouseFilm = createCategory(id++, "棚膜", film.getId(), 2, 2, 0, 0);
        categories.add(greenhouseFilm);
        // 农膜 -> 遮阳网
        Category shadeNet = createCategory(id++, "遮阳网", film.getId(), 2, 3, 0, 0);
        categories.add(shadeNet);
        // 农膜 -> 防虫网
        Category insectNet = createCategory(id++, "防虫网", film.getId(), 2, 4, 0, 0);
        categories.add(insectNet);

        // ===== 二级分类 - 农机 =====
        // 农机 -> 生产机械
        Category productionMachine = createCategory(id++, "生产机械", machinery.getId(), 2, 1, 0, 0);
        categories.add(productionMachine);
        // 农机 -> 运输机械
        Category transportMachine = createCategory(id++, "运输机械", machinery.getId(), 2, 2, 0, 0);
        categories.add(transportMachine);
        // 农机 -> 加工机械
        Category processingMachine = createCategory(id++, "加工机械", machinery.getId(), 2, 3, 0, 0);
        categories.add(processingMachine);

        // ===== 三级分类 - 种子 -> 粮食种子 =====
        // 粮食种子 -> 谷物类
        Category cereal = createCategory(id++, "谷物类", grainSeeds.getId(), 3, 1, 0, 0);
        categories.add(cereal);
        // 粮食种子 -> 杂粮类
        Category coarseGrain = createCategory(id++, "杂粮类", grainSeeds.getId(), 3, 2, 0, 0);
        categories.add(coarseGrain);
        // 粮食种子 -> 豆类作物
        Category legume = createCategory(id++, "豆类作物", grainSeeds.getId(), 3, 3, 0, 0);
        categories.add(legume);

        // ===== 三级分类 - 种子 -> 蔬菜种子 =====
        // 蔬菜种子 -> 叶菜类
        Category leafyVegetable = createCategory(id++, "叶菜类", vegetableSeeds.getId(), 3, 1, 0, 0);
        categories.add(leafyVegetable);
        // 蔬菜种子 -> 根茎类
        Category rootVegetable = createCategory(id++, "根茎类", vegetableSeeds.getId(), 3, 2, 0, 0);
        categories.add(rootVegetable);
        // 蔬菜种子 -> 茄果类
        Category solanaceous = createCategory(id++, "茄果类", vegetableSeeds.getId(), 3, 3, 0, 0);
        categories.add(solanaceous);
        // 蔬菜种子 -> 瓜类
        Category gourd = createCategory(id++, "瓜类", vegetableSeeds.getId(), 3, 4, 0, 0);
        categories.add(gourd);
        // 蔬菜种子 -> 豆类蔬菜
        Category beanVegetable = createCategory(id++, "豆类蔬菜", vegetableSeeds.getId(), 3, 5, 0, 0);
        categories.add(beanVegetable);
        // 蔬菜种子 -> 菌类
        Category mushroom = createCategory(id++, "菌类", vegetableSeeds.getId(), 3, 6, 0, 0);
        categories.add(mushroom);
        // 蔬菜种子 -> 芽苗类
        Category sprout = createCategory(id++, "芽苗类", vegetableSeeds.getId(), 3, 7, 0, 0);
        categories.add(sprout);

        // ===== 三级分类 - 肥料 -> 单质肥料 =====
        // 单质肥料 -> 氮肥
        Category nitrogenFertilizer = createCategory(id++, "氮肥", singleFertilizer.getId(), 3, 1, 0, 0);
        categories.add(nitrogenFertilizer);
        // 单质肥料 -> 磷肥
        Category phosphateFertilizer = createCategory(id++, "磷肥", singleFertilizer.getId(), 3, 2, 0, 0);
        categories.add(phosphateFertilizer);
        // 单质肥料 -> 钾肥
        Category potashFertilizer = createCategory(id++, "钾肥", singleFertilizer.getId(), 3, 3, 0, 0);
        categories.add(potashFertilizer);

        // ===== 四级分类 - 种子 -> 具体作物品种 =====
        // 谷物类 -> 具体作物
        Category rice = createCategory(id++, "水稻", cereal.getId(), 4, 1, 0, 0);
        categories.add(rice);
        Category wheat = createCategory(id++, "小麦", cereal.getId(), 4, 2, 0, 0);
        categories.add(wheat);
        Category corn = createCategory(id++, "玉米", cereal.getId(), 4, 3, 0, 0);
        categories.add(corn);
        Category millet = createCategory(id++, "谷子", cereal.getId(), 4, 4, 0, 0);
        categories.add(millet);

        // 杂粮类 -> 具体作物
        Category sorghum = createCategory(id++, "高粱", coarseGrain.getId(), 4, 1, 0, 0);
        categories.add(sorghum);
        Category buckwheat = createCategory(id++, "荞麦", coarseGrain.getId(), 4, 2, 0, 0);
        categories.add(buckwheat);

        // 豆类作物 -> 具体作物
        Category soybean = createCategory(id++, "大豆", legume.getId(), 4, 1, 0, 0);
        categories.add(soybean);
        Category pea = createCategory(id++, "豌豆", legume.getId(), 4, 2, 0, 0);
        categories.add(pea);

        // 叶菜类 -> 具体作物
        Category cabbage = createCategory(id++, "白菜", leafyVegetable.getId(), 4, 1, 0, 0);
        categories.add(cabbage);
        Category lettuce = createCategory(id++, "生菜", leafyVegetable.getId(), 4, 2, 0, 0);
        categories.add(lettuce);
        Category spinach = createCategory(id++, "菠菜", leafyVegetable.getId(), 4, 3, 0, 0);
        categories.add(spinach);

        // 根茎类 -> 具体作物
        Category carrot = createCategory(id++, "胡萝卜", rootVegetable.getId(), 4, 1, 0, 0);
        categories.add(carrot);
        Category radish = createCategory(id++, "萝卜", rootVegetable.getId(), 4, 2, 0, 0);
        categories.add(radish);
        Category potato = createCategory(id++, "土豆", rootVegetable.getId(), 4, 3, 0, 0);
        categories.add(potato);

        // 茄果类 -> 具体作物
        Category tomato = createCategory(id++, "番茄", solanaceous.getId(), 4, 1, 0, 0);
        categories.add(tomato);
        Category eggplant = createCategory(id++, "茄子", solanaceous.getId(), 4, 2, 0, 0);
        categories.add(eggplant);
        Category pepper = createCategory(id++, "辣椒", solanaceous.getId(), 4, 3, 0, 0);
        categories.add(pepper);

        // 瓜类 -> 具体作物
        Category cucumber = createCategory(id++, "黄瓜", gourd.getId(), 4, 1, 0, 0);
        categories.add(cucumber);
        Category melon = createCategory(id++, "西瓜", gourd.getId(), 4, 2, 0, 0);
        categories.add(melon);
        Category pumpkin = createCategory(id++, "南瓜", gourd.getId(), 4, 3, 0, 0);
        categories.add(pumpkin);

        // ===== 一级分类 - 畜禽（动物分类，用于饲料/兽药的适用动物选择） =====
        Category animal = createCategory(id++, "畜禽", 0L, 1, 8, 0, 0);
        categories.add(animal);

        // ===== 二级分类 - 畜禽 =====
        Category animalLivestock = createCategory(id++, "家畜", animal.getId(), 2, 1, 0, 0);
        categories.add(animalLivestock);
        Category animalPoultry = createCategory(id++, "家禽", animal.getId(), 2, 2, 0, 0);
        categories.add(animalPoultry);
        Category animalAquatic = createCategory(id++, "水产", animal.getId(), 2, 3, 0, 0);
        categories.add(animalAquatic);
        Category animalOther = createCategory(id++, "其他", animal.getId(), 2, 4, 0, 0);
        categories.add(animalOther);

        // ===== 三级分类 - 畜禽 -> 家畜 =====
        categories.add(createCategory(id++, "猪", animalLivestock.getId(), 3, 1, 0, 0));
        categories.add(createCategory(id++, "黄牛", animalLivestock.getId(), 3, 2, 0, 0));
        categories.add(createCategory(id++, "奶牛", animalLivestock.getId(), 3, 3, 0, 0));
        categories.add(createCategory(id++, "山羊", animalLivestock.getId(), 3, 4, 0, 0));
        categories.add(createCategory(id++, "绵羊", animalLivestock.getId(), 3, 5, 0, 0));
        categories.add(createCategory(id++, "马", animalLivestock.getId(), 3, 6, 0, 0));
        categories.add(createCategory(id++, "驴", animalLivestock.getId(), 3, 7, 0, 0));
        categories.add(createCategory(id++, "兔", animalLivestock.getId(), 3, 8, 0, 0));

        // ===== 三级分类 - 畜禽 -> 家禽 =====
        categories.add(createCategory(id++, "鸡", animalPoultry.getId(), 3, 1, 0, 0));
        categories.add(createCategory(id++, "鸭", animalPoultry.getId(), 3, 2, 0, 0));
        categories.add(createCategory(id++, "鹅", animalPoultry.getId(), 3, 3, 0, 0));
        categories.add(createCategory(id++, "鸽子", animalPoultry.getId(), 3, 4, 0, 0));

        // ===== 三级分类 - 畜禽 -> 水产 =====
        categories.add(createCategory(id++, "淡水鱼", animalAquatic.getId(), 3, 1, 0, 0));
        categories.add(createCategory(id++, "虾", animalAquatic.getId(), 3, 2, 0, 0));
        categories.add(createCategory(id++, "蟹", animalAquatic.getId(), 3, 3, 0, 0));

        // ===== 三级分类 - 畜禽 -> 其他 =====
        categories.add(createCategory(id++, "蜜蜂", animalOther.getId(), 3, 1, 0, 0));
        categories.add(createCategory(id++, "鹿", animalOther.getId(), 3, 2, 0, 0));

        return categories;
    }

    /**
     * 创建分类对象
     */
    private Category createCategory(Long id, String name, Long parentId, Integer level,
                                    Integer sortOrder, Integer status, Integer isCustom) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setParentId(parentId);
        category.setLevel(level);
        category.setSortOrder(sortOrder);
        category.setStatus(status);
        category.setIsCustom(isCustom);
        return category;
    }
}
