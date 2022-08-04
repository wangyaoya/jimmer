---
sidebar_position: 2
title: jimmer-core初体验
---

## 引入依赖

<Tabs groupId="language">
<TabItem value="java" label="Java">

```groovy title="build.gradle"
depdencies {
    
    implementation 'org.babyfish.jimmer:jimmer-sql:0.0.35'
    annotationProcessor 'org.babyfish.jimmer:jimmer-apt:0.0.35'

    runtimeOnly 'com.h2database:h2:2.1.212'
}
```

</TabItem>
<TabItem value="kotlin" label="Kotlin">

```kotlin title="build.gradle.kts"
plugins {
    // 第一步: 添加ksp插件
	id("com.google.devtools.ksp") version "1.7.10-1.0.6"

    ...省略其他插件...
}
depdencies {
    
    // 第二步: 添加jimmer-sql-kotlin
    implementation("org.babyfish.jimmer:jimmer-sql-kotlin:0.1.5")

    // 第三步: 应用ksp插件
	ksp("org.babyfish.jimmer:jimmer-ksp:0.1.5")

    ...ommit other dependency...
}

// 第四部: 讲生成的代码添加到编译目录中。
// 没有这个配置，gradle命令仍然可以正常执行，
// 但是, Intellij无法找到生成的源码。
kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}
```

</TabItem>
</Tabs>

## 声明不可变模型

```java title="TreeNode.java"
package yourpackage;

import org.babyfish.jimmer.Immutable;

@Immutable
public interface TreeNode {
    String name();
    TreeNode parent();
    List<TreeNode> childNodes();
}
```

:::info
这里，属性name, parent和childNodes采用了java record的命名规则，不以get开头。

你也可采用传统java bean的命名规则，以get开头。诸如：getName, getParent, getChildNodes。

无论你采用那种风格，jimmer-core都接受。建议采用java record那种不以get开头的命名风格，因为没有了set，get自然就没了意义。

无论采用哪种风格，都应该保证单个项目内部风格一致。
:::

## 使用

jimmer-core使用java annotation processor生成可变代理（下面代码中的TreeNodeDraft）。利用可变代理，开发人员可以使用命令式的代码简单地“修改”不可变对象。

```java
// 第一步，从头构建全新的数据
TreeNode treeNode = TreeNodeDraft.$.produce(root -> {
    root.setName("Root").addIntoChildNodes(food -> {
        food
            .setName("Food")
            .addIntoChildNodes(drink -> {
                drink
                    .setName("Drink")
                    .addIntoChildNodes(cococola -> {
                        cococola.setName("Coco Cola");
                    })
                    .addIntoChildNodes(fanta -> {
                        fanta.setName("Fanta");
                    });
                ;
            });
        ;
    });
});

// 第二步，基于现有数据对象，做某些“变更”，创建新的数据对象。
TreeNode newTreeNode = TreeNodeDraft.$.produce(
        // highlight-next-line
        treeNode, // 现有的数据对象
        root -> {
            root
                .childNodes(false).get(0) // Food
                .childNodes(false).get(0) // Drink
                .childNodes(false).get(0) // Coco Cola
                .setName("Coco Cola plus");
        }
);

System.out.println("treeNode:" + treeNode);
System.out.println("newTreeNode:" + newTreeNode);
```

输出结果（实际打印结果是紧凑的，但为了方便阅读，这里进行了格式化）

```javascript
treeNode: {
    "name":"Root",
    "childNodes":[
        {
            "name":"Food",
            "childNodes":[
                {
                    "name":"Drink",
                    "childNodes":[
                        // highlight-next-line
                        {"name":"Coco Cola"},
                        {"name":"Fanta"}
                    ]
                }
            ]
        }
    ]
}
newTreeNode: {
    "name":"Root",
    "childNodes":[
        {
            "name":"Food",
            "childNodes":[
                {
                    "name":"Drink",
                    "childNodes":[
                        // highlight-next-line
                        {"name":"Coco Cola plus"},
                        {"name":"Fanta"}
                    ]
                }
            ]
        }
    ]
}
```

用户定义的类型TreeNode是不可变类型； 但是AnnotationProcessor自动生成的类型TreeNodeDraft是可变类型，用户可以非常简单地修改它们（这些可以直接修改的Draft对象就是示例中各lambda表达式的参数）。

:::info
1. Draft对象非常轻量，仅仅是一个代理。它使用copy-on-write策略，所以并不会无条件地复制旧对象的数据，而是只有第一次被修改时才会复制旧对象的数据。

2. 对于一个巨大的对象树，只有根对象的代理才是一定会被创建的，其他的子代理对象根据用户代码的读取操作按需创建。

3. 最终，只有被修改过且修改前后新旧值不等的代理及其父代理链，会被用于重新创建新的不可变对象；未被修改的代理只是简单地返回其持有对旧对象的引用。 也就是说，未更改的子树与原始子树完全共享复用。
:::