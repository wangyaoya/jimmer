---
sidebar_position: 4
title: 对象抓取器
---

:::tip
对象抓取器是jimmer-sql一个非常强大的特征，具备可媲美GraphQL的能力。

即使用户不采用任何GraphQL相关的技术栈，也能在SQL查询层面得到和GraphQL相似的对象图查询能力。
:::

对象抓取器和以下这类技术类似，但更加强大
- [JPA的EntityGraph](https://www.baeldung.com/jpa-entity-graph)
- [ADO.NET EntityFramework的Include](https://docs.microsoft.com/en-us/dotnet/api/system.data.objects.objectquery-1.include?view=netframework-4.8)
- [ActiveRecord的include](https://guides.rubyonrails.org/active_record_querying.html#includes)

虽然在查询中返回整个对象的代码很简单，但是默认对象格式往往不能很好地符合开发需求。

- 我们不需要的对象属性被获取了，形成了浪费。这叫over fetch问题。
- 我们需要的对象属性被并未被获取，处于不可用的Unloaded状态。这叫under fetch问题。

对象抓取器很好地解决这个问题，让查询返回的对象既不over fetch也不under fetch。

## 基本使用

### 抓取标量字段

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                // highlight-next-line
                book.fetch(
                    BookFetcher.$.name()
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

:::note
Annotation processor会为每一个实体接口自动生成一个Fetcher类，在这个例子中，就是`BookFetcher`
:::

生成的SQL如下:

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME 
from BOOK as tb_1_ 
where tb_1_.EDITION = ?
```

:::note
Java代码没有调用BookFetcher的`id()`方法，但是，我们看到SQL语句仍然查询了对象的id属性。

id属性被特殊对待，总是会被查询，并不受对象抓取器的控制。

事实上，自动生成BookFetcher类中也没有`id()`方法，因为不需要。
:::

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":"Learning GraphQL"
}
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

### 抓取多个字段

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    // highlight-next-line
                    BookFetcher.$.name().price()
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

:::note
对象抓取器是不可变对象，每一次链式调用都会返回一个新的对象抓取器。

即，上述代码中
1. `BookFetcher.$`
2. `BookFetcher.$.name()`
3. `BookFetcher.$.name().price()`

是三个不同的对象抓取器，每一个都是不可变的。

对象抓取器是不可变对象，所以你可以借助静态变量随意共享对象抓取器。
:::

生成的SQL如下:

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME,
    tb_1_.PRICE  
from BOOK as tb_1_ 
where tb_1_.EDITION = ?
```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":"Learning GraphQL",
    "price":51.00
}
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

### allScalarFields

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    // highlight-next-line
                    BookFetcher.$.allScalarFields()
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

`allScalarFields()`用于加载所有非关联字段。

:::info
由于实际项目中对象的字段往往很多，所以`allScalarFields()`非常有用。
:::

生成的SQL如下:

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.EDITION, 
    tb_1_.PRICE   
from BOOK as tb_1_ 
where tb_1_.EDITION = ?
```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:
```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":"Learning GraphQL",
    "edition":3,
    "price":51.00
}
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

### 负属性

前面讲过的属性都是正属性，不断新增要查询的字段。而负属性则相反，从在已有的对象抓取器的基础上，删除指定属性。
```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    BookFetcher.$
                        .allScalarFields()
                        // highlight-next-line
                        .edition(false)
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

`edition(false)`使用参数false，这就是负属性。

- `allScalarFields()`的属性是`id + name + edition + price`
- `edition(false)`表示`-edition`

所以，最终并在一起，最终被抓取的属性是`id + name + price`

:::note
1. 对于正属性而言，`edition()`和`edition(true)`等价。
2. 当大部分属性需要抓取，只有少部分属性不需要抓取时，负属性会非常有用。
:::

生成的SQL如下:

```sql
select 
    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.PRICE   
from BOOK as tb_1_ 
where tb_1_.EDITION = ?
```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":"Learning GraphQL",
    "price":51.00
}
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

### allTableFields

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    // highlight-next-line
                    BookFetcher.$.allTableFields()
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

`allTableFields()`包含表定义的所有属性，包含标量属性、以及基于外键的多对一属性（当然，父对象只有id）。

:::note
`allTableFields()`返回的就是默认对象格式，所以以下两个select调用等价

1. 
    ```
    q.select(
        book.fetch(
            BookFetcher.$.allTableFields()
        )
    )
    ```

2. 
    ```
    q.select(
        book
    )
    ```
:::

生成的SQL如下:
```sql
select 
    tb_1_.ID, 
    tb_1_.NAME, 
    tb_1_.EDITION, 
    tb_1_.PRICE, 
    tb_1_.STORE_ID 
from BOOK as tb_1_ 
where tb_1_.EDITION = ?
```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:
```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":
    "Learning GraphQL",
    "edition":3,
    "price":51.00,
    "store":{"id":"d38c10da-6be8-4924-b9b9-5e81899612a0"}
}
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

和默认对象格式一样，基于外键的多对一属性被设置成了只有id属性的父对象。

### 抓取仅包含id的关联对象。

针对基于外键的关联如何实现这个功能我们已经讲解过，所以，让我们来看一个针对多对多关联`Book.authors`的例子。

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    BookFetcher.$.
                        .allScalarFields()
                        // highlight-next-line
                        .authors()
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

这里，`authors()`表示对多对多关联进行抓取。注意，并未指定任何参数，这表示只抓取关联对象的id属性。

生成两条SQL:

1. 查询`Book`对象本身
    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 
        tb_1_.STORE_ID 
    from BOOK as tb_1_ 
    where tb_1_.EDITION = ?
    ```

2. 根据关联`Book.authors`查询仅包含id的所有`Author`对象

    ```sql
    select 
        tb_1_.BOOK_ID, /* batch-map key */
        tb_1_.AUTHOR_ID /* batch-map value */
    from BOOK_AUTHOR_MAPPING as tb_1_ 
        where tb_1_.BOOK_ID in (?, ?, ?, ?)
    ```

这个例子说明了以下问题

1. 当前查询仅需要关联对象的id，也没有使用过滤器（过滤器是后文将会讲解的概念）。

    jimmer-sql会对这种情况进行优化，仅仅查询中间表`BOOK_AUTHOR_MAPPING`，而没有查询`AUTHOR`表。

2. `where tb_1_.BOOK_ID in (?, ?, ?, ?)`是批量查询，这是因为第一条查询返回主对象有4个。

    jimmer-sql使用批量查询来解决`N + 1`问题，这点和GraphQL的`DataLoader`一样。

    当一个批次的列表过于太长后，jimmer-sql会对进行分批切割，这会在后文的[Batch Size节](#batchsize)中讲解。

3. jimmer-sql通过额外的SQL去查询关联对象，而非在主查询的SQL中使用LEFT JOIN。

    这样设计的目的是为了防止对集合关联进行JOIN导致查询结果重复，因为这种数据重复对分页查询有毁灭性的破坏效果。

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":"Learning GraphQL",
    "edition":3,
    "price":51.00,
    "authors":[
        {"id":"1e93da94-af84-44f4-82d1-d8a9fd52ea94"},
        {"id":"fd6bb6cf-336d-416c-8005-1ae11a6694b5"}
    ]
}
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

### BatchSize

上面的例子中，我们看到这样的查询
```sql
select 
    tb_1_.BOOK_ID, 
    tb_1_.AUTHOR_ID 
from BOOK_AUTHOR_MAPPING as tb_1_ 
    where tb_1_.BOOK_ID in (?, ?, ?, ?)
```

这里，`in`表达式实现了批量查询，解决了`N + 1`问题。

如果一个批量太大，就会根据一个叫`batchSize`的设置进行分批切割，如

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    BookFetcher.$
                        .authors(
                            AuthorFetcher.$,
                            // highlight-next-line
                            it -> it.batch(2)
                        )
                )
            );
    })
    .execute();
```

:::danger
这里，`authors`关联的batchSize被设置为2。此配置会导致性能低下，这里仅仅为了演示，实际项目中请不要设置如此小的值。
:::

这样会导致`in(?, ?, ?, ?)`被切割为两个`in(?, ?)`，抓取关联对象的Sql会比分裂成两条。

1.
    ```sql
    select 
        tb_1_.BOOK_ID, 
        tb_1_.AUTHOR_ID 
    from BOOK_AUTHOR_MAPPING as tb_1_ 
        where tb_1_.BOOK_ID in (?, ?)
    ```

2.
    ```sql
    select 
        tb_1_.BOOK_ID, 
        tb_1_.AUTHOR_ID 
    from BOOK_AUTHOR_MAPPING as tb_1_ 
        where tb_1_.BOOK_ID in (?, ?)
    ```

实际开发中，绝大部分情况都不会这样设置batchSize，而是采用SqlClient中的全局配置。

1. `JSqlClient.getDefaultBatchSize()`: 一对一和多对一关联属性的默认batchSize，默认128
2. `JSqlClient.getDefaultListBatchSize()`: 一对多和多对多关联属性的默认batchSize，默认16

创建SqlClient时，可以更改全局配置:

```java
JSqlClient sqlClient = JSqlClient
    .newBuilder()
    .setDefaultBatchSize(256)
    .setDefaultListBatchSize(32)
    ....
    build();
```

:::caution
无论是对象抓取器级的batchSize，还是全局级的batchSize，都不要超过1000，因为Oracle数据库中`in(...)`最多允许1000个值。
:::


### 指定关联对象的字段

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    BookFetcher.$.
                        .allScalarFields()
                        .allScalarFields()
                        .store(
                            BookStoreFetcher.$
                                // highlight-next-line
                                .allScalarFields()
                        )
                        .authors(
                            AuthorFetcher.$
                                // highlight-next-line
                                .allScalarFields()
                        )
                )
            );
    })
    .execute();
books.forEach(System.out::println);
```

在这个查询中，我们对`Book.store`和`Book.authors`两个关联属性都要抓取，而且还进一步抓取`BookStore`和`Author`的所有非关联属性。

最终生成了3条SQL

1、查询`Book`对象本身
    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 
        tb_1_.STORE_ID 
    from BOOK as tb_1_ 
    where tb_1_.EDITION = ?
    ```

2、查询关联的`BookStore`对象
    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.WEBSITE 
    from BOOK_STORE as tb_1_ 
    where 
        tb_1_.ID in (?, ?)
    /* 主对象有4个，它们的外键去重后有两个 */
    ```

3、查询关联的`Author`对象
    ```sql
    select 
        
        /* batch-map key */
        tb_1_.BOOK_ID, 

        /* batch-map value */
        tb_1_.AUTHOR_ID, 
        tb_3_.FIRST_NAME, 
        tb_3_.LAST_NAME, 
        tb_3_.GENDER

    from BOOK_AUTHOR_MAPPING as tb_1_ 
    inner join AUTHOR as tb_3_ 
        on tb_1_.AUTHOR_ID = tb_3_.ID 
    where 
        tb_1_.BOOK_ID in (?, ?, ?, ?)
    ```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```
{
    "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
    "name":"Learning GraphQL",
    "edition":3,
    "price":51.00,
    "store":{
        "id":"d38c10da-6be8-4924-b9b9-5e81899612a0",
        "name":"O'REILLY",
        "website":null
    },
    "authors":[
        {
            "id":"1e93da94-af84-44f4-82d1-d8a9fd52ea94",
            "firstName":"Alex",
            "lastName":"Banks",
            "gender":"MALE"
        },{
            "id":"fd6bb6cf-336d-416c-8005-1ae11a6694b5",
            "firstName":"Eve",
            "lastName":"Procello",
            "gender":"MALE"
        }
    ]
},
...省略第2个对象...,
...省略第3个对象...,
...省略第4个对象...
```

:::info
从这个例子，我们可以看到，对象抓取器是一颗树，所以，关联对象的抓取深度没有限制。
:::

## 关联级分页

对于集合关联，可以在抓取属性时指定`limit(limit, offset)`，即关联级别的分页

:::caution
关联级分页和批量加载无法共存，因此，关联级分页必然导致`N + 1`问题，请谨慎使用此功能！

如果使用了关联级分页，必须把batchSize指定为1，否则会导致异常。此设计的目的在于让开发人员和阅读代码的人很清楚当前代码存在`N + 1`性能风险。
:::

```java
List<Book> books = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(
                book.fetch(
                    BookFetcher.$
                        .allScalarFields()
                        .authors(
                            AuthorFetcher.$.allScalarFields(),
                            // highlight-next-line
                            it -> it.batch(1).limit(10, 90)
                        )
                )
            );
    })
    .execute();
```

- 因关联分页无法解决`N + 1`问题，生成的SQL比较多
- 因不同数据库分页查询方法不同，为了简化讨论，这里假设方言使用了`H2Dialect`

1. 查询当前`Book`对象
    ```sql
    select 
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE 
    from BOOK as tb_1_ 
    where tb_1_.EDITION = ?
    ```

2. 对第1个`Book`对象的`authors`集合进行分页查询
    ```sql
    select 
        tb_1_.AUTHOR_ID, 
        tb_3_.FIRST_NAME, 
        tb_3_.LAST_NAME, 
        tb_3_.GENDER 
    from BOOK_AUTHOR_MAPPING as tb_1_ 
    inner join AUTHOR as tb_3_ 
        on tb_1_.AUTHOR_ID = tb_3_.ID 
    where tb_1_.BOOK_ID = ?
    /* highlight-next-line */ 
    limit ? offset ?
    ```

3. 对第2个`Book`对象的`authors`集合进行分页查询

    同上，略

4. 对第3个`Book`对象的`authors`集合进行分页查询

    同上，略

5. 对第4个`Book`对象的`authors`集合进行分页查询

    同上，略

## 过滤器

在抓取关联属性时，可以指定过滤器，为关联对象指定过滤条件。

这里，为了对比，我们让查询选取两列，两列都是`Book`类型。

- 第一列对象的`Book.autors`使用过滤器
- 第二列对象的`Book.autors`不使用过滤器

```java
List<Tuple2<Book, Book>> tuples = sqlClient
    .createQuery(BookTable.class, (q, book) -> {
        return q
            .where(book.edition().eq(3))
            .select(

                // First column
                book.fetch(
                    BookFetcher.$
                        .allScalarFields()
                        .authors(
                            AuthorFetcher.$
                                .allScalarFields(),

                            // Use filter here
                            // highlight-next-line
                            it -> it.filter(args -> {
                                args.where(
                                    args.getTable()
                                        .firstName().ilike("a")
                                );
                            })
                        )
                ),

                // Second column
                book.fetch(
                    BookFetcher.$
                        .allScalarFields()
                        .authors(
                            AuthorFetcher.$
                                    .allScalarFields()

                            // No filter here
                        )
                )
            );
    })
    .execute();
tuples.forEach(System.out::println);
```

生成三条SQL

1. 查询元组需要的两个`Book`对象

    ```sql
    select

        /* For tuple._1 */
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE, 

        /* For tuple._2 */
        tb_1_.ID, 
        tb_1_.NAME, 
        tb_1_.EDITION, 
        tb_1_.PRICE 

    from BOOK as tb_1_ 
    where tb_1_.EDITION = ?
    ```

2. 为第1列的4个`Book`对象查询`authors`关联属性，使用过滤器
    ```sql
    select 
        
        tb_1_.BOOK_ID, 

        tb_1_.AUTHOR_ID, 
        tb_3_.FIRST_NAME, 
        tb_3_.LAST_NAME, 
        tb_3_.GENDER 
    from BOOK_AUTHOR_MAPPING as tb_1_ 
    inner join AUTHOR as tb_3_ 
        on tb_1_.AUTHOR_ID = tb_3_.ID 
    where 
        tb_1_.BOOK_ID in (?, ?, ?, ?) 
    and 
        /* Use filter here */
        /* highlight-next-line */
        lower(tb_3_.FIRST_NAME) like ?
    ```

3. 为第2列的4个`Book`对象查询`authors`关联属性，不使用过滤器
    ```sql
    select 
        
        tb_1_.BOOK_ID, 

        tb_1_.AUTHOR_ID, 
        tb_3_.FIRST_NAME, 
        tb_3_.LAST_NAME, 
        tb_3_.GENDER 
    from BOOK_AUTHOR_MAPPING as tb_1_ 
    inner join AUTHOR as tb_3_ 
        on tb_1_.AUTHOR_ID = tb_3_.ID 
    where 
        tb_1_.BOOK_ID in (?, ?, ?, ?) 
    /* No filter here */
    ```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:
```
Tuple2{
    _1={
        "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
        "name":"Learning GraphQL",
        "edition":3,
        "price":51.00,
        "authors":[
            {
                "id":"1e93da94-af84-44f4-82d1-d8a9fd52ea94",
                "firstName":"Alex",
                "lastName":"Banks",
                "gender":"MALE"
            }
        ]
    }, 
    _2={
        "id":"64873631-5d82-4bae-8eb8-72dd955bfc56",
        "name":"Learning GraphQL",
        "edition":3,
        "price":51.00,
        "authors":[
            {
                "id":"1e93da94-af84-44f4-82d1-d8a9fd52ea94",
                "firstName":"Alex",
                "lastName":"Banks",
                "gender":"MALE"
            },{
                "id":"fd6bb6cf-336d-416c-8005-1ae11a6694b5",
                "firstName":"Eve",
                "lastName":"Procello",
                "gender":"MALE"
            }
        ]
    }
}
```

:::note
过滤器不仅可以筛选关联对象，还可以排序关联对象，原理类似，本文不做示范 
:::

:::caution

对于同时满足以下两个条件的关联属性
1. 多对一
2. 不为null

施加过滤器会导致异常
:::

## 自关联递归抓取

有一种常见的需求: 自关联。

从数据库角度讲，自关联表示一张表的外键引用其自身；从对象模型角度讲，自关联表示一颗树。

自关联的难点在于，对象深度无法控制，理论上讲，可以无限深。对此，jimmer-sql提供了良好的的支持。

### 模型和数据准备

定义实体接口

```java
@Entity
public interface TreeNode {

    @Id
    @Column(name = "NODE_ID", nullable = false)
    long id();

    String name();

    @ManyToOne
    TreeNode parent();

    @OneToMany(mappedBy = "parent")
    List<TreeNode> childNodes();
}
```

准备数据库
```sql
create table tree_node(
    node_id bigint not null,
    name varchar(20) not null,
    parent_id bigint
);
alter table tree_node
    add constraint pk_tree_node
        primary key(node_id);
alter table tree_node
    add constraint uq_tree_node
        unique(parent_id, name);
alter table tree_node
    add constraint fk_tree_node__parent
        foreign key(parent_id)
            references tree_node(node_id);

insert into tree_node(
    node_id, name, parent_id
) values
    (1, 'Home', null),
        (2, 'Food', 1),
            (3, 'Drinks', 2),
                (4, 'Coca Cola', 3),
                (5, 'Fanta', 3),
            (6, 'Bread', 2),
                (7, 'Baguette', 6),
                (8, 'Ciabatta', 6),
        (9, 'Clothing', 1),
            (10, 'Woman', 9),
                (11, 'Casual wear', 10),
                    (12, 'Dress', 11),
                    (13, 'Miniskirt', 11),
                    (14, 'Jeans', 11),
                (15, 'Formal wear', 10),
                    (16, 'Suit', 15),
                    (17, 'Shirt', 15),
            (18, 'Man', 9),
                (19, 'Casual wear', 18),
                    (20, 'Jacket', 19),
                    (21, 'Jeans', 19),
                (22, 'Formal wear', 18),
                    (23, 'Suit', 22),
                    (24, 'Shirt', 22)
;
```

### 有限深度

自顶向下，抓取两层

```java
List<TreeNode> treeNodes = sqlClient
    .createQuery(TreeNodeTable.class, (q, node) -> {
        q.where(node.parent().isNull());
        return q.select(
            node.fetch(
                TreeNodeFetcher.$
                    .name()
                    .childNodes(
                        TreeNodeFetcher.$.name(),
                        // highlight-next-line
                        it -> it.depth(2)
                    )
            )
        );
    })
    .execute();
treeNodes.forEach(System.out::println);
```

生成3条SQL

1. 主查询获取根节点（第0层）

    ```sql
    select 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID is null
    ```

2. 抓取第1层

    ```sql
    select 
        
        tb_1_.PARENT_ID,

        tb_1_.NODE_ID, 
        tb_1_.NAME

    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?)
    ```

3. 抓取第2层

    ```sql
    select 
        tb_1_.PARENT_ID, 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?)
    ```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:
```json
{
    "id":1,
    "name":"Home",
    "childNodes":[
        {
            "id":9,
            "name":"Clothing",
            "childNodes":[
                {"id":18,"name":"Man"},
                {"id":10,"name":"Woman"}
            ]
        },{
            "id":2,
            "name":"Food",
            "childNodes":[
                {"id":6,"name":"Bread"},
                {"id":3,"name":"Drinks"}
            ]
        }
    ]
}
```
### 无限递归

自顶向下，抓取无穷层

```java
List<TreeNode> treeNodes = sqlClient
    .createQuery(TreeNodeTable.class, (q, node) -> {
        q.where(node.parent().isNull());
        return q.select(
            node.fetch(
                TreeNodeFetcher.$
                    .name()
                    .childNodes(
                        TreeNodeFetcher.$.name(),
                        // highlight-next-line
                        it -> it.recursive()
                    )
            )
        );
    })
    .execute();
treeNodes.forEach(System.out::println);
```

:::note
上述代码中`it.recursive()`也可以写作`it.depth(Integer.MAX_VALUE)`，二者完全等价
:::

生成6条SQL

1. 查询根节点

    ```sql
    select 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID is null
    ```

2. 抓取第1层

    ```sql
    select 
        
        tb_1_.PARENT_ID,

        tb_1_.NODE_ID, 
        tb_1_.NAME

    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?)
    ```

3. 抓取第2层

    ```sql
    select 
        tb_1_.PARENT_ID, 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?)
    ```

4. 抓取第3层

    ```sql
    select 
        tb_1_.PARENT_ID, 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?, ?, ?)
    ```

5. 抓取第4层

    ```sql
    select 
        tb_1_.PARENT_ID, 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?, ?, ?, ?, ?, ?, ?)
    ```

6. 抓取第5层

    ```sql
    select 
        tb_1_.PARENT_ID, 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?, ?, ?, ?, ?, ?, ?, ?)
    ```
打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```json
{
    "id":1,"name":
    "Home","childNodes":[
        {
            "id":9,
            "name":"Clothing",
            "childNodes":[
                {
                    "id":18,
                    "name":"Man",
                    "childNodes":[
                        {
                            "id":19,
                            "name":"Casual wear",
                            "childNodes":[
                                {"id":20,"name":"Jacket","childNodes":[]},
                                {"id":21,"name":"Jeans","childNodes":[]}
                            ]
                        },{
                            "id":22,
                            "name":"Formal wear",
                            "childNodes":[
                                {"id":24,"name":"Shirt","childNodes":[]},
                                {"id":23,"name":"Suit","childNodes":[]}
                            ]
                        }
                    ]
                },{
                    "id":10,
                    "name":"Woman",
                    "childNodes":[
                        {
                            "id":11,
                            "name":"Casual wear",
                            "childNodes":[
                                {"id":12,"name":"Dress","childNodes":[]},
                                {"id":14,"name":"Jeans","childNodes":[]},
                                {"id":13,"name":"Miniskirt","childNodes":[]}
                            ]
                        },{
                            "id":15,
                            "name":"Formal wear",
                            "childNodes":[
                                {"id":17,"name":"Shirt","childNodes":[]},
                                {"id":16,"name":"Suit","childNodes":[]}
                            ]
                        }
                    ]
                }
            ]
        },{
            "id":2,
            "name":"Food",
            "childNodes":[
                {
                    "id":6,
                    "name":"Bread",
                    "childNodes":[
                        {"id":7,"name":"Baguette","childNodes":[]},
                        {"id":8,"name":"Ciabatta","childNodes":[]}
                    ]
                },{
                    "id":3,
                    "name":"Drinks",
                    "childNodes":[
                        {"id":4,"name":"Coca Cola","childNodes":[]},
                        {"id":5,"name":"Fanta","childNodes":[]}
                    ]
                }
            ]
        }
    ]
}
```

### 开发人员控制每个节点是否递归

自顶向下，抓取无穷层。但是，对名称为“Clothing”的节点，放弃递归

```java
List<TreeNode> treeNodes = sqlClient
    .createQuery(TreeNodeTable.class, (q, node) -> {
        q.where(node.parent().isNull());
        return q.select(
            node.fetch(
                TreeNodeFetcher.$
                    .name()
                    .childNodes(
                        TreeNodeFetcher.$.name(),
                        // highlight-next-line
                        it -> it.recursive(args ->
                            !args.getEntity().name().equals("Clothing")
                        )
                    )
            )
        );
    })
    .execute();
treeNodes.forEach(System.out::println);
```

上述代码中，`it.recursive(args)`的参数是一个lambda表达式，其参数`args`是一个对象，提供两个属性

1. `args.getEntity()`：当前节点对象。
2. `args.getDepth()`：当前节点深度。对于通过主查询直接得到的节点而言是0，随着递归的深入不断增大。
3. 用户决定的返回值：一个boolean变量，决定当前是否对当前节点进行递归抓取。

上述代码的含义是，除了`Clothing`节点外，其余节都会被递归抓取。

生成了5条SQL

1. 查询根对象

    ```sql
    select 
        tb_1_.NODE_ID, 
        tb_1_.NAME 
    from TREE_NODE as tb_1_ 
    where tb_1_.PARENT_ID is null
    ```

2. 抓取第1层
    ```sql
    select 

        tb_1_.PARENT_ID, 
        
        tb_1_.NODE_ID, 
        tb_1_.NAME 

    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?)
    ```

3. 抓取第2层
    ```sql
    select 

        tb_1_.PARENT_ID, 
        
        tb_1_.NODE_ID, 
        tb_1_.NAME 

    from TREE_NODE as tb_1_ 
    where 
        /* 
         * Home node has two child nodes:
         *      "Food" and "Clothing",
         * 
         * However, "Clothing is excluded by user,
         * so `in(?)` is used here, not `in(?, ?)`
         */
        tb_1_.PARENT_ID in (?)
    ```
4. 抓取第3层
    ```sql
    select 

        tb_1_.PARENT_ID, 
        
        tb_1_.NODE_ID, 
        tb_1_.NAME 

    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?)
    ```
5. 抓取第4层
    ```sql
    select 

        tb_1_.PARENT_ID, 
        
        tb_1_.NODE_ID, 
        tb_1_.NAME 

    from TREE_NODE as tb_1_ 
    where 
        tb_1_.PARENT_ID in (?, ?, ?, ?)
    ```

打印的结果如下（原输出是紧凑的，为了方便阅读，这里进行了格式化）:

```json
{
    "id":1,
    "name":"Home",
    "childNodes":[
        // highlight-next-line
        {"id":9,"name":"Clothing"},
        {
            "id":2,
            "name":"Food",
            "childNodes":[
                {
                    "id":6,
                    "name":"Bread",
                    "childNodes":[
                        {"id":7,"name":"Baguette","childNodes":[]},
                        {"id":8,"name":"Ciabatta","childNodes":[]}
                    ]
                },{
                    "id":3,
                    "name":"Drinks",
                    "childNodes":[
                        {"id":4,"name":"Coca Cola","childNodes":[]},
                        {"id":5,"name":"Fanta","childNodes":[]}
                    ]
                }
            ]
        }
    ]
}
```

## 和GraphQL对比

GraphQL是一套规范，对具体的实现方式没有任何限制，两者本没有可比性。

但如果用仅限于讨论使用关系性数据库实现GraphQL，两者之间就有了可比性，对比如下

||对象抓取器|GraphQL|
|--|--|--|
|利用查询生命周期内短生命周期缓存，防止重复加载数据|支持|支持|
|利用批量加载解决`N + 1`查询问题|支持|支持|
|不同的批量加载任务并行执行|不支持|支持|
|在关联级别添加配置或参数过滤关联对象|支持|支持|
|对自关联进行递归抓取|支持|不支持|

二者设计目的不同、侧重点不同、使用场景也不同。

1. 对象抓取器: 一次查询内部所有数据抓取行为全部基于同一个数据库连接执行，即便当前数据库事务尚未提交，仍然能基于当前数据库连接抓取最新数据。

2. GraphQL: 只针对已经提交的数据进行抓取，但可以利用多个不同的数据库连接，让不同部分的数据加载任务可以并发执行。

:::info
事实上，抛开本文所讨论的对象抓取器不谈，jimmer-sql对GraphQL也给予很好的支持。为加速Spring GraphQL的开发效率，提供了专门的API支持。请查看[对Spring GraphQL的支持](../spring-graphql)以了解更多。
:::