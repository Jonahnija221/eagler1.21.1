import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public final class PatchBrowserClasslib {
	private static final int ASM = Opcodes.ASM9;

	private PatchBrowserClasslib() {
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 3) {
			throw new IllegalArgumentException("usage: PatchBrowserClasslib <joml.jar> <teavm-classlib.jar> <output-dir>");
		}

		Path outputDir = Path.of(args[2]);
		patchMemUtil(Path.of(args[0]), outputDir);
		patchTMath(Path.of(args[1]), outputDir);
		patchSimpleClasslibMethods(Path.of(args[1]), outputDir);
		writeSpliteratorsAbstractSpliterator(outputDir);
		writeThreadState(outputDir);
		writeLegacyJavaxSecurityCertificates(outputDir);
		writeUnsafe(outputDir);
	}

	private static void patchMemUtil(Path jomlJar, Path outputDir) throws IOException {
		byte[] original = readClass(jomlJar, "org/joml/MemUtil.class");
		ClassReader reader = new ClassReader(original);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		reader.accept(new ClassVisitor(ASM, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (name.equals("createInstance") && descriptor.equals("()Lorg/joml/MemUtil;")) {
					MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
					mv.visitCode();
					mv.visitTypeInsn(Opcodes.NEW, "org/joml/MemUtil$MemUtilNIO");
					mv.visitInsn(Opcodes.DUP);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/joml/MemUtil$MemUtilNIO", "<init>", "()V", false);
					mv.visitInsn(Opcodes.ARETURN);
					mv.visitMaxs(2, 0);
					mv.visitEnd();
					return null;
				}
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}
		}, 0);
		writeClass(outputDir, "org/joml/MemUtil.class", writer.toByteArray());
	}

	private static void patchTMath(Path teavmClasslibJar, Path outputDir) throws IOException {
		byte[] original = readClass(teavmClasslibJar, "org/teavm/classlib/java/lang/TMath.class");
		ClassReader reader = new ClassReader(original);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		reader.accept(new ClassVisitor(ASM, writer) {
			private boolean hasFloatFma;
			private boolean hasDoubleFma;

			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (name.equals("fma") && descriptor.equals("(FFF)F")) {
					hasFloatFma = true;
				} else if (name.equals("fma") && descriptor.equals("(DDD)D")) {
					hasDoubleFma = true;
				}
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}

			@Override
			public void visitEnd() {
				if (!hasFloatFma) {
					MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fma", "(FFF)F", null, null);
					mv.visitCode();
					mv.visitVarInsn(Opcodes.FLOAD, 0);
					mv.visitVarInsn(Opcodes.FLOAD, 1);
					mv.visitInsn(Opcodes.FMUL);
					mv.visitVarInsn(Opcodes.FLOAD, 2);
					mv.visitInsn(Opcodes.FADD);
					mv.visitInsn(Opcodes.FRETURN);
					mv.visitMaxs(2, 3);
					mv.visitEnd();
				}
				if (!hasDoubleFma) {
					MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "fma", "(DDD)D", null, null);
					mv.visitCode();
					mv.visitVarInsn(Opcodes.DLOAD, 0);
					mv.visitVarInsn(Opcodes.DLOAD, 2);
					mv.visitInsn(Opcodes.DMUL);
					mv.visitVarInsn(Opcodes.DLOAD, 4);
					mv.visitInsn(Opcodes.DADD);
					mv.visitInsn(Opcodes.DRETURN);
					mv.visitMaxs(4, 6);
					mv.visitEnd();
				}
				super.visitEnd();
			}
		}, 0);
		writeClass(outputDir, "org/teavm/classlib/java/lang/TMath.class", writer.toByteArray());
	}

	private static void patchSimpleClasslibMethods(Path teavmClasslibJar, Path outputDir) throws IOException {
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/lang/TRuntime",
			new MethodSpec("maxMemory", "()J", mv -> {
				mv.visitLdcInsn(Long.valueOf(Integer.MAX_VALUE));
				mv.visitInsn(Opcodes.LRETURN);
			}),
			new MethodSpec("exec", "(Ljava/lang/String;)Lorg/teavm/classlib/java/lang/TProcess;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("exec", "([Ljava/lang/String;)Lorg/teavm/classlib/java/lang/TProcess;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("exec", "(Ljava/lang/String;[Ljava/lang/String;)Lorg/teavm/classlib/java/lang/TProcess;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("exec", "([Ljava/lang/String;[Ljava/lang/String;)Lorg/teavm/classlib/java/lang/TProcess;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("exec", "(Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Lorg/teavm/classlib/java/lang/TProcess;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("exec", "([Ljava/lang/String;[Ljava/lang/String;Ljava/io/File;)Lorg/teavm/classlib/java/lang/TProcess;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/lang/TSystem",
			new MethodSpec("mapLibraryName", "(Ljava/lang/String;)Ljava/lang/String;",
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitInsn(Opcodes.ARETURN);
				}),
			new MethodSpec("loadLibrary", "(Ljava/lang/String;)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
				mv.visitInsn(Opcodes.RETURN);
			}),
			new MethodSpec("load", "(Ljava/lang/String;)V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
				mv.visitInsn(Opcodes.RETURN);
			})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/lang/reflect/TField",
			new MethodSpec("getGenericType", "()Lorg/teavm/classlib/java/lang/reflect/TType;", mv -> {
				mv.visitVarInsn(Opcodes.ALOAD, 0);
				mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "org/teavm/classlib/java/lang/reflect/TField",
					"getType", "()Lorg/teavm/classlib/java/lang/TClass;", false);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("getInt", "(Ljava/lang/Object;)I", mv -> {
				mv.visitInsn(Opcodes.ICONST_0);
				mv.visitInsn(Opcodes.IRETURN);
			})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/lang/TClassLoader",
			new MethodSpec("loadClass", "(Ljava/lang/String;)Lorg/teavm/classlib/java/lang/TClass;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("getResources", "(Ljava/lang/String;)Ljava/util/Enumeration;", mv -> {
				mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
					"emptyEnumeration", "()Ljava/util/Enumeration;", false);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("getSystemResources", "(Ljava/lang/String;)Ljava/util/Enumeration;",
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
						"emptyEnumeration", "()Ljava/util/Enumeration;", false);
					mv.visitInsn(Opcodes.ARETURN);
			})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/lang/TThread",
			new MethodSpec("setContextClassLoader", "(Lorg/teavm/classlib/java/lang/TClassLoader;)V", mv -> {
				mv.visitInsn(Opcodes.RETURN);
			}),
			new MethodSpec("<init>",
				"(Lorg/teavm/classlib/java/lang/TThreadGroup;Lorg/teavm/classlib/java/lang/TRunnable;Ljava/lang/String;)V",
				mv -> {
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitVarInsn(Opcodes.ALOAD, 2);
					mv.visitVarInsn(Opcodes.ALOAD, 3);
					mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "org/teavm/classlib/java/lang/TThread",
						"<init>", "(Lorg/teavm/classlib/java/lang/TRunnable;Ljava/lang/String;)V", false);
					mv.visitInsn(Opcodes.RETURN);
				}),
			new MethodSpec("getState", "()Lorg/teavm/classlib/java/lang/TThread$State;", mv -> {
				mv.visitFieldInsn(Opcodes.GETSTATIC, "org/teavm/classlib/java/lang/TThread$State",
					"RUNNABLE", "Lorg/teavm/classlib/java/lang/TThread$State;");
				mv.visitInsn(Opcodes.ARETURN);
			})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/io/TFile",
			new MethodSpec("toPath", "()Ljava/nio/file/Path;", mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}),
			new MethodSpec("setReadable", "(ZZ)Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
			}),
			new MethodSpec("setReadable", "(Z)Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
			}),
			new MethodSpec("setWritable", "(ZZ)Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
			}),
			new MethodSpec("setExecutable", "(ZZ)Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
			}),
			new MethodSpec("setExecutable", "(Z)Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
			}),
			new MethodSpec("canExecute", "()Z", mv -> {
				mv.visitInsn(Opcodes.ICONST_1);
				mv.visitInsn(Opcodes.IRETURN);
			})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/nio/file/TFiles",
			new MethodSpec("getPosixFilePermissions",
				"(Lorg/teavm/classlib/java/nio/file/TPath;[Lorg/teavm/classlib/java/nio/file/TLinkOption;)Ljava/util/Set;",
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
					mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Collections",
						"emptySet", "()Ljava/util/Set;", false);
					mv.visitInsn(Opcodes.ARETURN);
				}),
			new MethodSpec("setPosixFilePermissions",
				"(Lorg/teavm/classlib/java/nio/file/TPath;Ljava/util/Set;)Lorg/teavm/classlib/java/nio/file/TPath;",
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
					mv.visitVarInsn(Opcodes.ALOAD, 0);
					mv.visitInsn(Opcodes.ARETURN);
				})
		);
		patchClass(teavmClasslibJar, outputDir, "org/teavm/classlib/java/util/TSpliterators",
			new MethodSpec("spliterator", "([DIII)Lorg/teavm/classlib/java/util/TSpliterator$OfDouble;",
				Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, mv -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			})
		);
	}

	private static void patchClass(Path jar, Path outputDir, String className, MethodSpec... methods) throws IOException {
		byte[] original = readClass(jar, className + ".class");
		ClassReader reader = new ClassReader(original);
		ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
		Set<String> existing = new HashSet<>();
		reader.accept(new ClassVisitor(ASM, writer) {
			@Override
			public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
				if (hasSpec(methods, name, descriptor)) {
					return null;
				}
				return super.visitMethod(access, name, descriptor, signature, exceptions);
			}

			@Override
			public void visitEnd() {
				for (MethodSpec method : methods) {
					if (!existing.contains(method.name + method.descriptor)) {
						MethodVisitor mv = super.visitMethod(method.access, method.name, method.descriptor, null, null);
						mv.visitCode();
						method.emitter.emit(mv);
						mv.visitMaxs(0, 0);
						mv.visitEnd();
					}
				}
				super.visitEnd();
			}
		}, 0);
		writeClass(outputDir, className + ".class", writer.toByteArray());
	}

	private static boolean hasSpec(MethodSpec[] methods, String name, String descriptor) {
		for (MethodSpec method : methods) {
			if (method.name.equals(name) && method.descriptor.equals(descriptor)) {
				return true;
			}
		}
		return false;
	}

	private static void writeSpliteratorsAbstractSpliterator(Path outputDir) throws IOException {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
			"org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator", null,
			"java/lang/Object", new String[] { "org/teavm/classlib/java/util/TSpliterator" });
		writer.visitInnerClass("org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator",
			"org/teavm/classlib/java/util/TSpliterators", "AbstractSpliterator",
			Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC);
		FieldVisitor size = writer.visitField(Opcodes.ACC_PRIVATE, "estimateSize", "J", null, null);
		size.visitEnd();
		FieldVisitor characteristics = writer.visitField(Opcodes.ACC_PRIVATE, "characteristics", "I", null, null);
		characteristics.visitEnd();
		MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(JI)V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.LLOAD, 1);
		mv.visitFieldInsn(Opcodes.PUTFIELD, "org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator", "estimateSize", "J");
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ILOAD, 3);
		mv.visitFieldInsn(Opcodes.PUTFIELD, "org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator", "characteristics", "I");
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "trySplit", "()Lorg/teavm/classlib/java/util/TSpliterator;", null, null);
		mv.visitCode();
		mv.visitInsn(Opcodes.ACONST_NULL);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "estimateSize", "()J", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, "org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator", "estimateSize", "J");
		mv.visitInsn(Opcodes.LRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "characteristics", "()I", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitFieldInsn(Opcodes.GETFIELD, "org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator", "characteristics", "I");
		mv.visitInsn(Opcodes.IRETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		writer.visitEnd();
		writeClass(outputDir, "org/teavm/classlib/java/util/TSpliterators$AbstractSpliterator.class", writer.toByteArray());
	}

	private static void writeThreadState(Path outputDir) throws IOException {
		String owner = "org/teavm/classlib/java/lang/TThread$State";
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM,
			owner, "Ljava/lang/Enum<L" + owner + ";>;", "java/lang/Enum", null);
		writer.visitOuterClass("org/teavm/classlib/java/lang/TThread", null, null);
		writer.visitInnerClass(owner, "org/teavm/classlib/java/lang/TThread", "State",
			Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM);
		String descriptor = "L" + owner + ";";
		String arrayDescriptor = "[L" + owner + ";";
		String[] names = { "NEW", "RUNNABLE", "BLOCKED", "WAITING", "TIMED_WAITING", "TERMINATED" };
		for (String name : names) {
			writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
				name, descriptor, null, null).visitEnd();
		}
		writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
			"$VALUES", arrayDescriptor, null, null).visitEnd();

		MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitVarInsn(Opcodes.ALOAD, 1);
		mv.visitVarInsn(Opcodes.ILOAD, 2);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()" + arrayDescriptor, null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, owner, "$VALUES", arrayDescriptor);
		mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, arrayDescriptor, "clone", "()Ljava/lang/Object;", false);
		mv.visitTypeInsn(Opcodes.CHECKCAST, arrayDescriptor);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "valueOf", "(Ljava/lang/String;)" + descriptor,
			null, null);
		mv.visitCode();
		mv.visitLdcInsn(org.objectweb.asm.Type.getType(descriptor));
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Enum", "valueOf",
			"(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;", false);
		mv.visitTypeInsn(Opcodes.CHECKCAST, owner);
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();

		mv = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		for (int i = 0; i < names.length; i++) {
			mv.visitTypeInsn(Opcodes.NEW, owner);
			mv.visitInsn(Opcodes.DUP);
			mv.visitLdcInsn(names[i]);
			pushInt(mv, i);
			mv.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, "<init>", "(Ljava/lang/String;I)V", false);
			mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, names[i], descriptor);
		}
		pushInt(mv, names.length);
		mv.visitTypeInsn(Opcodes.ANEWARRAY, owner);
		for (int i = 0; i < names.length; i++) {
			mv.visitInsn(Opcodes.DUP);
			pushInt(mv, i);
			mv.visitFieldInsn(Opcodes.GETSTATIC, owner, names[i], descriptor);
			mv.visitInsn(Opcodes.AASTORE);
		}
		mv.visitFieldInsn(Opcodes.PUTSTATIC, owner, "$VALUES", arrayDescriptor);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		writer.visitEnd();
		writeClass(outputDir, owner + ".class", writer.toByteArray());
	}

	private static void writeLegacyJavaxSecurityCertificates(Path outputDir) throws IOException {
		writeJavaxCertificate(outputDir);
		writeJavaxX509Certificate(outputDir);
	}

	private static void writeJavaxCertificate(Path outputDir) throws IOException {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
			"javax/security/cert/Certificate", null, "java/lang/Object", null);
		MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getEncoded", "()[B", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "verify",
			"(Ljava/security/PublicKey;)V", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "verify",
			"(Ljava/security/PublicKey;Ljava/lang/String;)V", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "toString",
			"()Ljava/lang/String;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getPublicKey",
			"()Ljava/security/PublicKey;", null, null).visitEnd();
		writer.visitEnd();
		writeClass(outputDir, "javax/security/cert/Certificate.class", writer.toByteArray());
	}

	private static void writeJavaxX509Certificate(Path outputDir) throws IOException {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
			"javax/security/cert/X509Certificate", null, "javax/security/cert/Certificate", null);
		MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "javax/security/cert/Certificate", "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "checkValidity", "()V", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "checkValidity",
			"(Ljava/util/Date;)V", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getVersion", "()I", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getSerialNumber",
			"()Ljava/math/BigInteger;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getIssuerDN",
			"()Ljava/security/Principal;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getSubjectDN",
			"()Ljava/security/Principal;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getNotBefore",
			"()Ljava/util/Date;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getNotAfter",
			"()Ljava/util/Date;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getSigAlgName",
			"()Ljava/lang/String;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getSigAlgOID",
			"()Ljava/lang/String;", null, null).visitEnd();
		writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "getSigAlgParams", "()[B", null, null).visitEnd();
		writer.visitEnd();
		writeClass(outputDir, "javax/security/cert/X509Certificate.class", writer.toByteArray());
	}

	private static void pushInt(MethodVisitor mv, int value) {
		switch (value) {
			case 0 -> mv.visitInsn(Opcodes.ICONST_0);
			case 1 -> mv.visitInsn(Opcodes.ICONST_1);
			case 2 -> mv.visitInsn(Opcodes.ICONST_2);
			case 3 -> mv.visitInsn(Opcodes.ICONST_3);
			case 4 -> mv.visitInsn(Opcodes.ICONST_4);
			case 5 -> mv.visitInsn(Opcodes.ICONST_5);
			default -> mv.visitLdcInsn(value);
		}
	}

	private static void writeUnsafe(Path outputDir) throws IOException {
		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, "sun/misc/Unsafe", null, "java/lang/Object", null);
		writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, "theUnsafe", "Lsun/misc/Unsafe;", null, null).visitEnd();
		MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
		mv.visitCode();
		mv.visitVarInsn(Opcodes.ALOAD, 0);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		mv = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
		mv.visitCode();
		mv.visitTypeInsn(Opcodes.NEW, "sun/misc/Unsafe");
		mv.visitInsn(Opcodes.DUP);
		mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "sun/misc/Unsafe", "<init>", "()V", false);
		mv.visitFieldInsn(Opcodes.PUTSTATIC, "sun/misc/Unsafe", "theUnsafe", "Lsun/misc/Unsafe;");
		mv.visitInsn(Opcodes.RETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
		addStaticUnsafeGetter(writer);
		String[] specs = {
			"objectFieldOffset(Ljava/lang/reflect/Field;)J", "staticFieldOffset(Ljava/lang/reflect/Field;)J",
			"staticFieldBase(Ljava/lang/reflect/Field;)Ljava/lang/Object;", "arrayBaseOffset(Ljava/lang/Class;)I",
			"arrayIndexScale(Ljava/lang/Class;)I", "addressSize()I", "pageSize()I",
			"allocateInstance(Ljava/lang/Class;)Ljava/lang/Object;",
			"compareAndSwapObject(Ljava/lang/Object;JLjava/lang/Object;Ljava/lang/Object;)Z",
			"compareAndSwapInt(Ljava/lang/Object;JII)Z", "compareAndSwapLong(Ljava/lang/Object;JJJ)Z",
			"getAndSetObject(Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;",
			"getAndAddInt(Ljava/lang/Object;JI)I", "getAndAddLong(Ljava/lang/Object;JJ)J",
			"getObject(Ljava/lang/Object;J)Ljava/lang/Object;", "putObject(Ljava/lang/Object;JLjava/lang/Object;)V",
			"getObjectVolatile(Ljava/lang/Object;J)Ljava/lang/Object;", "putObjectVolatile(Ljava/lang/Object;JLjava/lang/Object;)V",
			"putOrderedObject(Ljava/lang/Object;JLjava/lang/Object;)V",
			"getInt(Ljava/lang/Object;J)I", "putInt(Ljava/lang/Object;JI)V", "getIntVolatile(Ljava/lang/Object;J)I",
			"putIntVolatile(Ljava/lang/Object;JI)V", "putOrderedInt(Ljava/lang/Object;JI)V", "getInt(J)I", "putInt(JI)V",
			"getLong(Ljava/lang/Object;J)J", "putLong(Ljava/lang/Object;JJ)V", "getLongVolatile(Ljava/lang/Object;J)J",
			"putLongVolatile(Ljava/lang/Object;JJ)V", "putOrderedLong(Ljava/lang/Object;JJ)V", "getLong(J)J", "putLong(JJ)V",
			"getByte(Ljava/lang/Object;J)B", "putByte(Ljava/lang/Object;JB)V", "getByte(J)B", "putByte(JB)V",
			"getShort(Ljava/lang/Object;J)S", "putShort(Ljava/lang/Object;JS)V", "getShort(J)S", "putShort(JS)V",
			"getBoolean(Ljava/lang/Object;J)Z", "putBoolean(Ljava/lang/Object;JZ)V",
			"allocateMemory(J)J", "reallocateMemory(JJ)J", "freeMemory(J)V", "setMemory(JJB)V", "copyMemory(JJJ)V",
			"throwException(Ljava/lang/Throwable;)V", "loadFence()V", "storeFence()V", "fullFence()V"
		};
		for (String spec : specs) {
			int split = spec.indexOf('(');
			String name = spec.substring(0, split);
			String descriptor = spec.substring(split);
			addDefaultMethod(writer, Opcodes.ACC_PUBLIC, name, descriptor);
		}
		writer.visitEnd();
		writeClass(outputDir, "sun/misc/Unsafe.class", writer.toByteArray());
	}

	private static void addStaticUnsafeGetter(ClassWriter writer) {
		MethodVisitor mv = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getUnsafe", "()Lsun/misc/Unsafe;", null, null);
		mv.visitCode();
		mv.visitFieldInsn(Opcodes.GETSTATIC, "sun/misc/Unsafe", "theUnsafe", "Lsun/misc/Unsafe;");
		mv.visitInsn(Opcodes.ARETURN);
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static void addDefaultMethod(ClassWriter writer, int access, String name, String descriptor) {
		MethodVisitor mv = writer.visitMethod(access, name, descriptor, null, null);
		mv.visitCode();
		if (name.equals("getAndSetObject")) {
			mv.visitVarInsn(Opcodes.ALOAD, 3);
			mv.visitInsn(Opcodes.ARETURN);
		} else {
			emitDefaultReturn(mv, descriptor);
		}
		mv.visitMaxs(0, 0);
		mv.visitEnd();
	}

	private static void emitDefaultReturn(MethodVisitor mv, String descriptor) {
		char type = descriptor.charAt(descriptor.lastIndexOf(')') + 1);
		switch (type) {
			case 'V' -> mv.visitInsn(Opcodes.RETURN);
			case 'J' -> {
				mv.visitInsn(Opcodes.LCONST_0);
				mv.visitInsn(Opcodes.LRETURN);
			}
			case 'F' -> {
				mv.visitInsn(Opcodes.FCONST_0);
				mv.visitInsn(Opcodes.FRETURN);
			}
			case 'D' -> {
				mv.visitInsn(Opcodes.DCONST_0);
				mv.visitInsn(Opcodes.DRETURN);
			}
			case 'L', '[' -> {
				mv.visitInsn(Opcodes.ACONST_NULL);
				mv.visitInsn(Opcodes.ARETURN);
			}
			default -> {
				mv.visitInsn(Opcodes.ICONST_0);
				mv.visitInsn(Opcodes.IRETURN);
			}
		}
	}

	private record MethodSpec(String name, String descriptor, int access, MethodEmitter emitter) {
		MethodSpec(String name, String descriptor, MethodEmitter emitter) {
			this(name, descriptor, Opcodes.ACC_PUBLIC, emitter);
		}
	}

	@FunctionalInterface
	private interface MethodEmitter {
		void emit(MethodVisitor mv);
	}

	private static byte[] readClass(Path jar, String entry) throws IOException {
		try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
			java.util.jar.JarEntry jarEntry = jarFile.getJarEntry(entry);
			if (jarEntry == null) {
				throw new IOException("Missing " + entry + " in " + jar);
			}
			try (java.io.InputStream input = jarFile.getInputStream(jarEntry)) {
				return input.readAllBytes();
			}
		}
	}

	private static void writeClass(Path outputDir, String entry, byte[] bytes) throws IOException {
		Path path = outputDir.resolve(entry);
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}
}
