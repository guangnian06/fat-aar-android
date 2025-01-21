package com.kezong.fataar;

class FatAarExtension {

    /**
     * Used in RClassesTransform.java by reflection, don't change the name.
     */
    static final String NAME = "fataar"

    /**
     * Plan A: using bytecode patching to process the merging problem of R files
     * Plan B: generate sub module's R class to process the merging problem of R files
     * if transformR is true, use Plan A, else use Plan B.
     * In the future, Plan B maybe deprecated.
     *
     * Used in RClassesTransform.java by reflection, don't change the field name.
     * @since 1.3.0
     */
    boolean transformR = true

    /**
     * If transitive is true, local jar module and remote library's dependencies will be embed. (local aar module does not support)
     * If transitive is false, just embed first level dependency
     * Default value is false
     * @since 1.3.0
     */
    boolean transitive = false

    /**
     * Path mapping of shadow plugin
     *
     * Example:
     * {
     *   "com.shadow.before": "com.shadow.after",
     *   "com.shadow.old": "com.shadow.new"
     * }
     */
    Map<String, String> shadowPaths = new HashMap<>()

    /**
     * 需要删除declare-style属性的format键值对, 包名前缀，用于优化匹配性能，为空则全量匹配
     * group:modulename:version
     *
     * 比如：此demo中为
     * group：example
     * modulename：lib-aar、lib-aar2
     * version：1.0.0，没有默认 unspecified
     *
     * 示例错误： [attr/exampleTextColor] /Users/xxx/github/fat-aar-android/example/lib-main/build/intermediates/exploded-aar/example/lib-aar/unspecified/flavor2Debug/res/values/values.xml
     * [attr/exampleTextColor] /Users/xxx/github/fat-aar-android/example/lib-main/build/intermediates/exploded-aar/example/lib-aar2/unspecified/flavor2Debug/res/values/values.xml:
     * Error: Duplicate resources
     *
     * 则只需要配置 [example:lib-aar:unspecified] 和 [example:lib-aar2:unspecified]
     */
    public HashSet<String> excludeDeclareStyleAttrsFormatPath = new HashSet<>()

    /**
     * 需要删除declare-style属性的format键值对
     * key: declare-style name
     * value: attr name
     */
    public HashMap<String, String> excludeDeclareStyleAttrsFormat = new HashMap<>()
}
