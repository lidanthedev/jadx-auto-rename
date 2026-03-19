package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.info.MethodInfo
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.ArgType
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.InsnUtils
import jadx.plugins.renamer.util.RenameUtils
import java.util.logging.Logger

class IntrinsicsRenamePass(
	private val options: Options = Options(),
) : JadxDecompilePass {
	private val logger = Logger.getLogger("IntrinsicsRenamePass")

	private lateinit var root: RootNode
	private val intrinsicsClassRawNames = HashSet<String>()
	private val argRenameRules = HashMap<String, ArgRenameRule>()

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"IntrinsicsRename",
			"Detect and rename Kotlin Intrinsics class and methods"
		).after("SimplifyVisitor")
	}

	override fun init(root: RootNode) {
		this.root = root
		intrinsicsClassRawNames.clear()
		argRenameRules.clear()

		val intrinsicsClasses = findIntrinsicsClasses(root)
		for (cls in intrinsicsClasses) {
			intrinsicsClassRawNames.add(cls.classInfo.rawName)
			if (options.classRename) {
				renameIntrinsicsClass(cls)
			}
			detectAndApplyMethodRenames(cls)
		}
	}

	override fun visit(cls: ClassNode?): Boolean {
		return true
	}

	override fun visit(mth: MethodNode) {
		if (mth.contains(AFlag.DONT_RENAME) || mth.isNoCode || argRenameRules.isEmpty()) {
			return
		}
		for (block in mth.basicBlocks) {
			for (insn in block.instructions) {
				if (insn !is InvokeNode) {
					continue
				}
				val rule = argRenameRules[methodKey(insn.callMth)] ?: continue
				applyArgRename(insn, rule)
			}
		}
	}

	private fun findIntrinsicsClasses(root: RootNode): List<ClassNode> {
		val result = ArrayList<ClassNode>()
		for (cls in root.classes) {
			if (containsIntrinsicsMarker(cls)) {
				result.add(cls)
			}
		}
		return result
	}

	private fun containsIntrinsicsMarker(cls: ClassNode): Boolean {
		for (mth in cls.methods) {
			val insns = collectInsns(mth) ?: continue
			for (insn in insns) {
				val constVal = InsnUtils.getConstValueByInsn(root, insn)
				if (constVal is String && constVal.contains(INTRINSICS_MARKER_UPDATE_RUNTIME)) {
					return true
				}
			}
		}
		return false
	}

	private fun renameIntrinsicsClass(cls: ClassNode) {
		if (cls.contains(AFlag.DONT_RENAME) || cls.name == "Intrinsics") {
			return
		}
		try {
			if (cls.classInfo.hasAlias() && RenameUtils.isClassUserRenamed(cls)) {
				return
			}
		} catch (_: Exception) {
			// ignore
		}
		logger.info("Rename class '$cls' to 'Intrinsics' by IntrinsicsRenamePass")
		cls.rename("Intrinsics")
		RenameReasonAttr.forNode(cls).append("from IntrinsicsRenamePass")
	}

	private fun detectAndApplyMethodRenames(cls: ClassNode) {
		for (mth in cls.methods) {
			val detection = detectMethod(mth) ?: continue
			detection.renameTo?.let { renameTo ->
				renameMethod(mth, renameTo)
			}
			detection.argRenameRule?.let { rule ->
				argRenameRules[methodKey(mth.methodInfo)] = rule
			}
		}
	}

	private fun detectMethod(mth: MethodNode): MethodDetection? {
		val args = mth.methodInfo.argumentsTypes
		return when (args.size) {
			0 -> detectNoArgs(mth)
			1 -> detectOneArg(mth)
			2 -> detectTwoArgs(mth)
			3 -> detectThreeArgs(mth)
			else -> null
		}
	}

	private fun detectNoArgs(mth: MethodNode): MethodDetection? {
		val insns = collectInsns(mth) ?: return null
		when {
			hasCtorInit(insns, "java.lang.NullPointerException") -> return MethodDetection("throwJavaNpe")
			hasCtorInit(insns, "java.lang.AssertionError") -> return MethodDetection("throwAssert")
			hasCtorInit(insns, "java.lang.IllegalArgumentException") -> return MethodDetection("throwIllegalArgument")
			hasCtorInit(insns, "java.lang.IllegalStateException") -> return MethodDetection("throwIllegalState")
			hasConstString(insns, REIFIED_DIRECT_CALL_MSG) -> return MethodDetection("throwUndefinedForReified")
		}
		return null
	}

	private fun detectOneArg(mth: MethodNode): MethodDetection? {
		val arg = mth.methodInfo.argumentsTypes[0]
		if (arg == ArgType.THROWABLE) {
			return MethodDetection("sanitizeStackTrace")
		}
		if (arg == ArgType.OBJECT) {
			return MethodDetection("checkNotNull")
		}
		if (mth.methodInfo.returnType == ArgType.STRING) {
			return MethodDetection("createParameterIsNullExceptionMessage")
		}
		val insns = collectInsns(mth) ?: return null
		when {
			hasCtorInit(insns, "java.lang.UnsupportedOperationException") -> return MethodDetection("throwUndefinedForReified")
			hasCtorInit(insns, "java.lang.AssertionError") -> return MethodDetection("throwAssert")
			hasCtorInit(insns, "java.lang.IllegalStateException") -> return MethodDetection("throwIllegalState")
			hasCtorInit(insns, "java.lang.IllegalArgumentException") -> {
				return if (mth.accessFlags.isPublic) MethodDetection("throwIllegalArgument") else MethodDetection("throwParameterIsNullIAE")
			}
			hasCtorInit(insns, "java.lang.NullPointerException") -> {
				return if (mth.accessFlags.isPublic) MethodDetection("throwJavaNpe") else MethodDetection("throwParameterIsNullNPE")
			}
			hasConstString(insns, "lateinit property ") -> return MethodDetection("throwUninitializedPropertyAccessException")
		}
		return null
	}

	private fun detectTwoArgs(mth: MethodNode): MethodDetection? {
		val args = mth.methodInfo.argumentsTypes
		when (mth.methodInfo.returnType) {
			ArgType.BOOLEAN -> return MethodDetection("areEqual")
			ArgType.INT -> return MethodDetection("compare")
			ArgType.THROWABLE -> return MethodDetection("sanitizeStackTrace")
		}
		if (args[0] == ArgType.STRING && args[1] == ArgType.STRING) {
			return MethodDetection("checkHasClass")
		}
		if (args[0] == ArgType.STRING && args[1] == ArgType.OBJECT) {
			return MethodDetection("stringPlus")
		}
		if (args[0] == ArgType.INT && args[1] == ArgType.STRING) {
			return MethodDetection("reifiedOperationMarker")
		}

		val insns = collectInsns(mth) ?: return null
		if (hasCtorInit(insns, "java.lang.NullPointerException")) {
			return MethodDetection("checkNotNullExpressionValue", ArgRenameRule(1, 0, simple = true))
		}
		if (hasCtorInit(insns, "java.lang.IllegalStateException")) {
			return if (hasAnyConstString(insns)) {
				MethodDetection("checkExpressionValueIsNotNull", ArgRenameRule(1, 0, simple = true))
			} else {
				MethodDetection("checkReturnedValueOrFieldIsNotNull", ArgRenameRule(1, 0, simple = true))
			}
		}

		for (insn in insns) {
			if (insn is InvokeNode && insn.isStaticCall && insn.callMth.argsCount == 1) {
				val called = root.resolveMethod(insn.callMth) ?: continue
				val oneArgDetection = detectOneArg(called) ?: continue
				if (oneArgDetection.renameTo == "throwParameterIsNullIAE") {
					return MethodDetection("checkParameterIsNotNull", ArgRenameRule(1, 0))
				}
				if (oneArgDetection.renameTo == "throwParameterIsNullNPE") {
					return MethodDetection("checkNotNullParameter", ArgRenameRule(1, 0))
				}
			}
		}

		if (looksLikeObfuscatedNullCheck(mth, insns)) {
			return MethodDetection("checkNotNullParameter", ArgRenameRule(1, 0))
		}
		return null
	}

	private fun detectThreeArgs(mth: MethodNode): MethodDetection? {
		val args = mth.methodInfo.argumentsTypes
		if (args[0] == ArgType.INT && args[1] == ArgType.STRING && args[2] == ArgType.STRING) {
			return MethodDetection("reifiedOperationMarker")
		}
		val insns = collectInsns(mth) ?: return null
		if (hasConstString(insns, "Method specified as non-null returned null: ")) {
			return MethodDetection("checkReturnedValueIsNotNull")
		}
		if (hasConstString(insns, "Field specified as non-null is null: ")) {
			return MethodDetection("checkFieldIsNotNull", ArgRenameRule(2, 0))
		}
		return null
	}

	private fun looksLikeObfuscatedNullCheck(mth: MethodNode, insns: List<InsnNode>): Boolean {
		if (!mth.accessFlags.isStatic || mth.methodInfo.returnType != ArgType.VOID) {
			return false
		}
		val args = mth.methodInfo.argumentsTypes
		if (args.size < 2 || args[1] != ArgType.STRING) {
			return false
		}
		val argRegs = runCatching { mth.argRegs }.getOrNull() ?: return false
		if (argRegs.size < 2) {
			return false
		}
		val firstArgReg = argRegs[0].regNum
		val secondArgReg = argRegs[1].regNum

		val hasNullCheck = insns.any { insn ->
			if (insn.type != InsnType.IF || insn.argsCount < 2) {
				false
			} else {
				(isRegNum(insn.getArg(0), firstArgReg) && insn.getArg(1).isZeroConst)
					|| (isRegNum(insn.getArg(1), firstArgReg) && insn.getArg(0).isZeroConst)
			}
		}
		if (!hasNullCheck) {
			return false
		}

		val secondArgUsed = insns.any { insn ->
			insn is InvokeNode && insn.argList.any { arg -> isRegNum(arg, secondArgReg) }
		}
		if (!secondArgUsed) {
			return false
		}
		return insns.any { it.type == InsnType.THROW } || insns.any { insn ->
			insn is InvokeNode && isThrowHelper(insn.callMth)
		}
	}

	private fun isThrowHelper(callMth: MethodInfo): Boolean {
		val mth = root.resolveMethod(callMth) ?: return false
		val insns = collectInsns(mth) ?: return false
		return insns.any { it.type == InsnType.THROW }
	}

	private fun applyArgRename(invoke: InvokeNode, rule: ArgRenameRule) {
		if (invoke.argsCount <= rule.nameArgIndex || invoke.argsCount <= rule.targetArgIndex) {
			return
		}
		val constName = InsnUtils.getConstValueByArg(root, invoke.getArg(rule.nameArgIndex)) as? String ?: return
		val normalizedName = if (rule.simple) {
			normalizeSimpleName(constName)
		} else {
			normalizeName(constName)
		} ?: return
		val target = invoke.getArg(rule.targetArgIndex)
		if (target is RegisterArg) {
			renameRegister(target, normalizedName)
		}
	}

	private fun renameMethod(mth: MethodNode, newName: String) {
		if (!NameMapper.isValidIdentifier(newName) || mth.name == newName) {
			return
		}
		if (mth.methodInfo.isConstructor || mth.methodInfo.isClassInit) {
			return
		}
		try {
			if (mth.methodInfo.hasAlias() && RenameUtils.isMethodUserRenamed(mth)) {
				return
			}
		} catch (_: Exception) {
			// ignore
		}
		logger.info("Rename method '$mth' to '$newName' by IntrinsicsRenamePass")
		mth.rename(newName)
		RenameReasonAttr.forNode(mth).append("from IntrinsicsRenamePass")
	}

	private fun renameRegister(arg: RegisterArg, newName: String) {
		val sVar = arg.sVar ?: return
		val codeVar = sVar.codeVar
		if (codeVar.isThis || codeVar.name == newName) {
			return
		}
		codeVar.name = newName
	}

	private fun collectInsns(mth: MethodNode): List<InsnNode>? {
		mth.instructions?.let { return it.filterNotNull() }
		mth.basicBlocks?.let { blocks ->
			if (blocks.isNotEmpty()) {
				return blocks.flatMap { block -> block.instructions }
			}
		}
		if (!mth.isLoaded) {
			runCatching { mth.load() }.onFailure { return null }
		}
		return mth.instructions?.filterNotNull()
	}

	private fun hasCtorInit(insns: List<InsnNode>, className: String): Boolean {
		return insns.any { insn ->
			insn is InvokeNode
				&& insn.callMth.isConstructor
				&& insn.callMth.declClass.fullName == className
		}
	}

	private fun hasAnyConstString(insns: List<InsnNode>): Boolean {
		return insns.any { insn -> InsnUtils.getConstValueByInsn(root, insn) is String }
	}

	private fun hasConstString(insns: List<InsnNode>, exact: String): Boolean {
		return insns.any { insn ->
			val constVal = InsnUtils.getConstValueByInsn(root, insn)
			constVal is String && constVal == exact
		}
	}

	private fun normalizeName(input: String): String? {
		val trimmed = input.trim()
		if (trimmed.isEmpty()) {
			return null
		}
		if (NameMapper.isValidAndPrintable(trimmed)) {
			return trimmed
		}
		val token = IDENTIFIER_REGEX.find(trimmed)?.value ?: return null
		if (!NameMapper.isValidAndPrintable(token)) {
			return null
		}
		return token
	}

	private fun normalizeSimpleName(input: String): String? {
		val trimmed = input.trim()
		if (!SIMPLE_IDENTIFIER_REGEX.matches(trimmed)) {
			return null
		}
		return trimmed
	}

	private fun isRegNum(arg: jadx.core.dex.instructions.args.InsnArg, regNum: Int): Boolean {
		if (arg is RegisterArg) {
			return arg.regNum == regNum
		}
		if (arg is jadx.core.dex.instructions.args.InsnWrapArg) {
			return arg.wrapInsn.argList.any { nested -> isRegNum(nested, regNum) }
		}
		return false
	}

	private fun methodKey(mth: MethodInfo): String {
		return mth.declClass.rawName + "->" + mth.shortId
	}

	private data class MethodDetection(
		val renameTo: String? = null,
		val argRenameRule: ArgRenameRule? = null,
	)

	private data class ArgRenameRule(
		val nameArgIndex: Int,
		val targetArgIndex: Int,
		val simple: Boolean = false,
	)

	data class Options(
		val classRename: Boolean = true,
	)

	companion object {
		private const val INTRINSICS_MARKER_UPDATE_RUNTIME = "Please update the Kotlin runtime to the latest version"
		private const val REIFIED_DIRECT_CALL_MSG = "This function has a reified type parameter and thus can only be inlined at compilation time, not called directly."
		private val IDENTIFIER_REGEX = Regex("[A-Za-z_][A-Za-z0-9_]*")
		private val SIMPLE_IDENTIFIER_REGEX = Regex("[A-Za-z0-9_$]+")
	}
}

