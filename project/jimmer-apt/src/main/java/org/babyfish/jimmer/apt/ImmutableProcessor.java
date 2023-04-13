package org.babyfish.jimmer.apt;

import org.babyfish.jimmer.apt.generator.*;
import org.babyfish.jimmer.apt.meta.ImmutableType;
import org.babyfish.jimmer.apt.meta.MetaException;
import org.babyfish.jimmer.error.ErrorFamily;
import org.babyfish.jimmer.sql.Entity;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "org.babyfish.jimmer.Immutable",
        "org.babyfish.jimmer.sql.Entity",
        "org.babyfish.jimmer.sql.MappedSuperclass",
        "org.babyfish.jimmer.error.ErrorFamily"
})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ImmutableProcessor extends AbstractProcessor {

    private Context context;

    private Filer filer;

    private String[] includes = null;

    private String[] excludes = null;

    private Messager messager;

    private boolean processed;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();
        String includes = processingEnv.getOptions().get("jimmer.source.includes");
        String excludes = processingEnv.getOptions().get("jimmer.source.excludes");
        if (includes != null && !includes.isEmpty()) {
            this.includes = includes.trim().split("\\s*,\\s*");
        }
        if (excludes != null && !excludes.isEmpty()) {
            this.excludes = excludes.trim().split("\\s*,\\s*");
        }
        context = new Context(
                processingEnv.getElementUtils(),
                processingEnv.getTypeUtils(),
                "true".equals(processingEnv.getOptions().get("jimmer.keepIsPrefix"))
        );
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv
    ) {
        if (!processed) {
            processed = true;
        } else {
            return true;
        }

        try {
            Map<TypeElement, ImmutableType> immutableTypeMap = parseImmutableTypes(roundEnv);
            generateJimmerTypes(
                    roundEnv
                            .getRootElements()
                            .stream()
                            .filter(it -> it instanceof TypeElement)
                            .map(immutableTypeMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList()),
                    roundEnv
            );

            List<TypeElement> errorElements = getErrorFamilies(roundEnv);
            generateErrorType(errorElements);
        } catch (MetaException ex) {
            messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), ex.getElement());
        }

        return true;
    }

    private Map<TypeElement, ImmutableType> parseImmutableTypes(RoundEnvironment roundEnv) {
        Map<TypeElement, ImmutableType> map = new HashMap<>();
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                if (context.isImmutable(typeElement) && include(typeElement)) {
                    if (typeElement.getKind() != ElementKind.INTERFACE) {
                        throw new MetaException(
                                typeElement,
                                "immutable type must be interface"
                        );
                    }
                    ImmutableType immutableType = context.getImmutableType(typeElement);
                    map.put(typeElement, immutableType);
                }
            }
        }
        int step = 0;
        while (true) {
            boolean hasNext = false;
            for (ImmutableType type : map.values()) {
                hasNext |= type.resolve(context, step);
            }
            if (!hasNext) {
                break;
            }
            step++;
        }
        return map;
    }

    private List<TypeElement> getErrorFamilies(RoundEnvironment roundEnv) {
        List<TypeElement> typeElements = new ArrayList<>();
        for (Element element : roundEnv.getRootElements()) {
            if (element instanceof TypeElement && include((TypeElement) element)) {
                if (element.getAnnotation(ErrorFamily.class) != null) {
                    if (element.getKind() != ElementKind.ENUM) {
                        throw new MetaException(
                                element,
                                "only enum can be decorated by @" +
                                        ErrorFamily.class.getName()
                        );
                    }
                    typeElements.add((TypeElement) element);
                }
            }
        }
        return typeElements;
    }

    private boolean include(TypeElement typeElement) {
        String qualifiedName = typeElement.getQualifiedName().toString();
        if (includes != null) {
            boolean matched = false;
            for (String include : includes) {
                if (qualifiedName.startsWith(include)) {
                    return true;
                }
            }
        }
        if (excludes != null) {
            for (String exclude : excludes) {
                if (qualifiedName.startsWith(exclude)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void generateJimmerTypes(Collection<ImmutableType> immutableTypes, RoundEnvironment roundEnv) {
        for (ImmutableType immutableType : immutableTypes) {
            new DraftGenerator(
                    immutableType,
                    filer
            ).generate();
            new PropsGenerator(
                    context,
                    immutableType,
                    filer
            ).generate();
            messager.printMessage(Diagnostic.Kind.NOTE, "Immutable: " + immutableType.getQualifiedName());
            if (immutableType.isEntity()) {
                messager.printMessage(Diagnostic.Kind.NOTE, "Entity: " + immutableType.getQualifiedName());
                new TableGenerator(
                        context,
                        immutableType,
                        false,
                        filer
                ).generate();
                new TableGenerator(
                        context,
                        immutableType,
                        true,
                        filer
                ).generate();
                new FetcherGenerator(
                        context,
                        immutableType,
                        filer
                ).generate();
            } else if (immutableType.isEmbeddable()) {
                new PropExpressionGenerator(
                        context,
                        immutableType,
                        filer
                ).generate();
            }
        }
        PackageCollector packageCollector = new PackageCollector();
        for (Element element : roundEnv.getElementsAnnotatedWith(Entity.class)) {
            packageCollector.accept((TypeElement) element);
        }
        new JimmerModuleGenerator(
                packageCollector.toString(),
                packageCollector.getTypeElements(),
                filer
        ).generate();
    }

    private void generateErrorType(List<TypeElement> typeElements) {
        for (TypeElement typeElement : typeElements) {
            new ErrorGenerator(typeElement, filer).generate();
        }
    }

    private static class PackageCollector {

        private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

        private List<String> paths;

        private String str;

        private final List<TypeElement> typeElements = new ArrayList<>();

        public void accept(TypeElement typeElement) {
            typeElements.add(typeElement);
            if (paths != null && paths.isEmpty()) {
                return;
            }
            str = null;
            List<String> newPaths = Collections.emptyList();
            for (Element parent = typeElement.getEnclosingElement(); parent != null; parent = parent.getEnclosingElement()) {
                if (parent instanceof PackageElement) {
                    String packageName = ((PackageElement) parent).getQualifiedName().toString();
                    newPaths = new ArrayList<>(Arrays.asList(DOT_PATTERN.split(packageName)));
                    break;
                }
            }
            if (paths == null) {
                paths = newPaths;
            } else {
                int len = Math.min(paths.size(), newPaths.size());
                int index = 0;
                while (index < len) {
                    if (!paths.get(index).equals(newPaths.get(index))) {
                        break;
                    }
                    index++;
                }
                if (index < paths.size()) {
                    paths.subList(index, paths.size()).clear();
                }
            }
        }

        public List<TypeElement> getTypeElements() {
            return Collections.unmodifiableList(typeElements);
        }

        @Override
        public String toString() {
            String s = str;
            if (s == null) {
                List<String> ps = paths;
                str = s = ps == null || ps.isEmpty() ? "" : String.join(".", ps);
            }
            return s;
        }
    }
}
