package jadx.plugins.renamer.passes

import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxDecompilePass
import jadx.core.deobf.NameMapper
import jadx.core.dex.attributes.AFlag
import jadx.core.dex.attributes.nodes.RenameReasonAttr
import jadx.core.dex.info.FieldInfo
import jadx.core.dex.instructions.ConstStringNode
import jadx.core.dex.instructions.IndexInsnNode
import jadx.core.dex.instructions.InsnType
import jadx.core.dex.instructions.InvokeNode
import jadx.core.dex.instructions.args.InsnArg
import jadx.core.dex.instructions.args.InsnWrapArg
import jadx.core.dex.instructions.args.RegisterArg
import jadx.core.dex.instructions.mods.ConstructorInsn
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.InsnNode
import jadx.core.dex.nodes.MethodNode
import jadx.core.dex.nodes.RootNode
import jadx.core.utils.BlockUtils
import jadx.core.utils.InsnUtils
import jadx.plugins.renamer.util.RenameUtils
import java.util.logging.Level
import java.util.logging.Logger

class ToStringRenamePass() : JadxDecompilePass {
	val logger = Logger.getLogger("ToStringRename")

	override fun init(root: RootNode) {
	}

	override fun visit(cls: ClassNode?): Boolean {
		return true
	}

	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"ToStringRename",
			"Rename classes and fields according to toString() output"
		)
			.after("SimplifyVisitor")
	}

	override fun visit(mth: MethodNode) {
		// bind parent class once to avoid repeated dereferences and NPEs
		val parentCls = mth.parentClass ?: return
		if (parentCls.contains(AFlag.DONT_RENAME)) {
			return
		}
		if (mth.contains(AFlag.DONT_RENAME)) {
			return
		}
		if (mth.methodInfo.shortId == "toString()Ljava/lang/String;") {
			val returnBlock = mth.exitBlock.predecessors.firstOrNull { it.contains(AFlag.RETURN) }
			val lastInsn = returnBlock?.instructions?.lastOrNull()
			if (lastInsn != null && lastInsn.type == InsnType.RETURN) {
				val arg = lastInsn.getArg(0)
				if (arg.isInsnWrap) {
					val wrapInsn = (arg as InsnWrapArg).wrapInsn
					when (wrapInsn.type) {
						InsnType.STR_CONCAT -> {
							logger.info("Renaming using STR_CONCAT toString in class: $parentCls")
							processArgs(parentCls, wrapInsn)
						}

						InsnType.INVOKE -> {
							// Try to handle StringBuilder pattern
							if (isStringBuilderToString(wrapInsn)) {
								logger.info("Renaming using StringBuilder toString in class: $parentCls")
								processStringBuilderPattern(parentCls, mth, wrapInsn as InvokeNode)
							}
						}

						else -> {}
					}
				}
			}
		}
	}

	val clsSepRgx = Regex("[ ({:]")

	private fun isStringBuilderToString(insn: InsnNode): Boolean {
		if (insn !is InvokeNode) {
			return false
		}
		val methodInfo = insn.callMth
		return methodInfo.name == "toString" &&
			methodInfo.declClass.fullName == "java.lang.StringBuilder"
	}

	private fun processStringBuilderPattern(parentCls: ClassNode, mth: MethodNode, toStringInsn: InvokeNode): Boolean {
		try {
			// Get the StringBuilder instance that toString() was called on
			val sbInstance = toStringInsn.getArg(0)

			// Handle both InsnWrap (single expression chain) and RegisterArg (multi-statement pattern)
			if (sbInstance.isInsnWrap) {
				// Single expression pattern: new StringBuilder("x").append(...).append(...).toString()
				val sbLastInsn = (sbInstance as InsnWrapArg).wrapInsn
				if (sbLastInsn !is InvokeNode) {
					return false
				}
				return processInstructionChain(parentCls, mth, sbLastInsn)
			} else if (sbInstance is RegisterArg) {
				// Multi-statement pattern: sb = ...; sb.append(...); sb.append(...); return sb.toString()
				return processRegisterChain(parentCls, mth, sbInstance)
			}
			return false
		} catch (e: Exception) {
			logger.log(Level.SEVERE, "Error processing StringBuilder pattern", e)
			return false
		}
	}

	private fun processInstructionChain(parentCls: ClassNode, mth: MethodNode, sbLastInsn: InvokeNode): Boolean {
		// Work backwards through the append chain to collect all appends
		val appendChain = mutableListOf<InvokeNode>()
		var currentInsn: InsnNode? = sbLastInsn

		// Collect all append calls
		while (currentInsn != null && currentInsn is InvokeNode) {
			val methodInfo = currentInsn.callMth
			if (methodInfo.name == "append" && methodInfo.declClass.fullName == "java.lang.StringBuilder") {
				appendChain.add(currentInsn)
				// Move to the previous instruction (the StringBuilder being appended to)
				val sbArg = currentInsn.getArg(0)
				currentInsn = if (sbArg.isInsnWrap) {
					(sbArg as InsnWrapArg).wrapInsn
				} else {
					null
				}
			} else {
				break
			}
		}
		// Also check for StringBuilder constructor call
		if (currentInsn != null && currentInsn is InvokeNode) {
			val methodInfo = currentInsn.callMth
			if (methodInfo.name == "<init>" && methodInfo.declClass.fullName == "java.lang.StringBuilder") {
				val initialStr = if (currentInsn.argsCount >= 2) extractConstString(mth, currentInsn.getArg(1)) ?: "" else ""
				extractAndRenameFromStringBuilderChain(parentCls, mth, initialStr, appendChain.asReversed())
				return true
			}
		}
		return false
	}

	private fun processRegisterChain(parentCls: ClassNode, mth: MethodNode, sbRegister: RegisterArg): Boolean {
		try {
			val appendChain = mutableListOf<InvokeNode>()
			val appendSet = linkedSetOf<InvokeNode>()
			var constructorInitialStr: String? = null

			// Keep instruction order stable for pair parsing (label append -> field append)
			val blocks = BlockUtils.buildSimplePath(mth.enterBlock)
			for (block in blocks) {
				for (insn in block.instructions) {
					if (insn is ConstructorInsn
						&& insn.isNewInstance
						&& insn.callMth.declClass.fullName == "java.lang.StringBuilder"
						&& insn.argsCount >= 1
					) {
						constructorInitialStr = extractConstString(mth, insn.getArg(0))
						continue
					}

					// Look for invocations
					if (insn.type == InsnType.INVOKE && insn is InvokeNode) {
						val methodInfo = insn.callMth

						// Check if it's StringBuilder.append on our register
						if (methodInfo.name == "append" &&
							methodInfo.declClass.fullName == "java.lang.StringBuilder" &&
							insn.argsCount >= 2
						) {
							val instanceArg = insn.getArg(0)
							// Accept direct receiver and chained receiver: sb.append("x").append(...)
							if (isBuilderRootArg(instanceArg, sbRegister.regNum)) {
								// Include nested append calls from receiver chain so label/value pairs are visible.
								for (appendCall in collectAppendCallsInOrder(insn)) {
									if (appendSet.add(appendCall)) {
										appendChain.add(appendCall)
									}
								}
							}
						}
						// Check for StringBuilder constructor on our register (if assigned from constructor)
						else if (methodInfo.name == "<init>" &&
							methodInfo.declClass.fullName == "java.lang.StringBuilder" &&
							insn.argsCount >= 1 &&
							isBuilderRootArg(insn.getArg(0), sbRegister.regNum)
						) {
							if (insn.argsCount >= 2) {
								constructorInitialStr = extractConstString(mth, insn.getArg(1))
							}
						}
					}
				}
			}
			// Process collected data
			if (appendChain.isNotEmpty()) {
				extractAndRenameFromStringBuilderChain(parentCls, mth, constructorInitialStr ?: "", appendChain)
				return true
			}
			return false
		} catch (e: Exception) {
			logger.log(Level.SEVERE, "Error processing register chain", e)
			return false
		}
	}

	private fun extractAndRenameFromStringBuilderChain(
		parentCls: ClassNode,
		mth: MethodNode,
		initialStr: String,
		appendChain: List<InvokeNode>
	) {
		try {
			// Extract class name from initial string (e.g., "MyDataClass{" -> "MyDataClass")
			if (initialStr.isNotBlank()) {
				val clsNameMatch = Regex("([a-zA-Z_][a-zA-Z0-9_]*)").find(initialStr)
				if (clsNameMatch != null) {
					val clsName = clsNameMatch.groupValues[1]
					if (NameMapper.isValidIdentifier(clsName)) {
						try {
							val info = parentCls.getClassInfo()
							if (info != null && info.hasAlias() && RenameUtils.isClassUserRenamed(parentCls)) {
								// don't override
							} else {
								logger.info("rename class '${parentCls.name}' to '$clsName'")
								parentCls.rename(clsName)
								RenameReasonAttr.forNode(parentCls).append("from toString()")
							}
						} catch (e: Exception) {
							logger.log(
								Level.SEVERE,
								"Error during class rename check in ToStringRenamePass for class ${parentCls.name}",
								e
							)
						}
					}
				}
			}

			// Parse append sequence in order: label append followed by field append.
			var pendingFieldName: String? = null
			for (appendInsn in appendChain) {
				if (appendInsn.argsCount < 2) {
					continue
				}
				val appendArg = appendInsn.getArg(1)

				// Some builds fold "label" + field into one append arg (often STR_CONCAT).
				val inlineMapping = extractInlineFieldMapping(mth, appendArg)
				if (inlineMapping != null) {
					val (inlineName, inlineFldInfo) = inlineMapping
					tryRenameField(parentCls, inlineFldInfo, inlineName)
					pendingFieldName = null
					continue
				}

				val labelName = extractFieldNameLabel(mth, appendArg)
				if (labelName != null) {
					pendingFieldName = labelName
					continue
				}

				if (pendingFieldName == null) {
					continue
				}

				val fldInfo = extractIGetFieldInfo(appendArg)
				if (fldInfo == null) {
					continue
				}
				val fieldName = pendingFieldName
				tryRenameField(parentCls, fldInfo, fieldName)
				pendingFieldName = null
			}
		} catch (e: Exception) {
			logger.severe("StringBuilder pattern process failed: $e")
		}
	}

	private fun tryRenameField(parentCls: ClassNode, fldInfo: FieldInfo, fieldName: String): Boolean {
		val fld = parentCls.searchField(fldInfo)
		if (fld == null) {
			logger.info("toString field mapping candidate: unresolved ${fldInfo.name} -> '$fieldName'")
			return false
		}
		if (!NameMapper.isValidIdentifier(fieldName)) {
			logger.info("toString field mapping candidate: invalid identifier '${fld.name}' -> '$fieldName'")
			return false
		}
		try {
			val finfo = fld.getFieldInfo()
			if (finfo != null && finfo.hasAlias() && RenameUtils.isFieldUserRenamed(fld)) {
				logger.info("toString field mapping skipped (user renamed): '${fld.name}' -> '$fieldName'")
				return false
			}
			logger.info("rename field '${fld.name}' to '$fieldName' from StringBuilder toString")
			fld.rename(fieldName)
			RenameReasonAttr.forNode(fld).append("from toString()")
			return true
		} catch (e: Exception) {
			logger.log(
				Level.SEVERE,
				"Error during field rename in ToStringRenamePass for field ${fld.name} in class ${parentCls.name}",
				e
			)
			return false
		}
	}

	private fun extractInlineFieldMapping(mth: MethodNode, arg: InsnArg): Pair<String, FieldInfo>? {
		val insn = if (arg is RegisterArg) arg.sVar.assignInsn else arg.unwrap()
		return extractInlineFieldMappingFromInsn(mth, insn)
	}

	private fun extractInlineFieldMappingFromInsn(mth: MethodNode, insn: InsnNode?): Pair<String, FieldInfo>? {
		if (insn == null) {
			return null
		}
		if ((insn.type == InsnType.MOVE || insn.type == InsnType.CHECK_CAST) && insn.argsCount >= 1) {
			return extractInlineFieldMapping(mth, insn.getArg(0))
		}
		if (insn.type != InsnType.STR_CONCAT) {
			return null
		}
		var fieldName: String? = null
		var fldInfo: FieldInfo? = null
		for (part in insn.arguments) {
			if (fieldName == null) {
				fieldName = extractFieldNameLabel(mth, part)
			}
			if (fldInfo == null) {
				fldInfo = extractIGetFieldInfo(part)
			}
		}
		return if (fieldName != null && fldInfo != null) {
			Pair(fieldName, fldInfo)
		} else {
			null
		}
	}

	private fun isBuilderRootArg(arg: InsnArg, regNum: Int): Boolean {
		if (arg is RegisterArg) {
			return arg.regNum == regNum
		}
		if (!arg.isInsnWrap) {
			return false
		}
		val insn = (arg as InsnWrapArg).wrapInsn
		if (insn !is InvokeNode) {
			return false
		}
		val methodInfo = insn.callMth
		if (methodInfo.declClass.fullName != "java.lang.StringBuilder") {
			return false
		}
		return when (methodInfo.name) {
			"append" -> insn.argsCount >= 1 && isBuilderRootArg(insn.getArg(0), regNum)
			"<init>" -> insn.argsCount >= 1 && isBuilderRootArg(insn.getArg(0), regNum)
			else -> false
		}
	}

	private fun collectAppendCallsInOrder(appendInvoke: InvokeNode): List<InvokeNode> {
		val result = mutableListOf<InvokeNode>()
		fun walk(inv: InvokeNode) {
			if (inv.argsCount >= 1) {
				val receiver = inv.getArg(0)
				if (receiver.isInsnWrap) {
					val wrapped = (receiver as InsnWrapArg).wrapInsn
					if (wrapped is InvokeNode
						&& wrapped.callMth.declClass.fullName == "java.lang.StringBuilder"
						&& wrapped.callMth.name == "append"
					) {
						walk(wrapped)
					}
				}
			}
			result.add(inv)
		}
		walk(appendInvoke)
		return result
	}

	private fun extractConstString(mth: MethodNode, arg: InsnArg): String? {
		val constVal = InsnUtils.getConstValueByArg(mth.root(), arg)
		if (constVal is String) {
			return constVal
		}
		return extractConstStringFromArg(arg)
	}

	private fun extractFieldNameLabel(mth: MethodNode, arg: InsnArg): String? {
		val str = extractConstString(mth, arg) ?: return null
		val match = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*=").findAll(str).lastOrNull() ?: return null
		return match.groupValues[1]
	}

	private fun extractIGetFieldInfo(arg: InsnArg): FieldInfo? {
		return extractIGetFieldInfoFromArg(arg)
	}

	private fun extractConstStringFromArg(arg: InsnArg): String? {
		if (arg is RegisterArg) {
			return extractConstStringFromInsn(arg.sVar.assignInsn)
		}
		val insn = arg.unwrap() ?: return null
		return extractConstStringFromInsn(insn)
	}

	private fun extractConstStringFromInsn(insn: InsnNode?): String? {
		if (insn == null) {
			return null
		}
		if (insn is ConstStringNode) {
			return insn.string
		}
		if ((insn.type == InsnType.MOVE || insn.type == InsnType.CHECK_CAST) && insn.argsCount >= 1) {
			return extractConstStringFromArg(insn.getArg(0))
		}
		return null
	}

	private fun extractIGetFieldInfoFromArg(arg: InsnArg): FieldInfo? {
		if (arg is RegisterArg) {
			return extractIGetFieldInfoFromInsn(arg.sVar.assignInsn)
		}
		val insn = arg.unwrap() ?: return null
		return extractIGetFieldInfoFromInsn(insn)
	}

	private fun extractIGetFieldInfoFromInsn(insn: InsnNode?): FieldInfo? {
		if (insn == null) {
			return null
		}
		if (insn is IndexInsnNode && insn.type == InsnType.IGET) {
			return insn.index as? FieldInfo
		}
		if ((insn.type == InsnType.MOVE || insn.type == InsnType.CHECK_CAST) && insn.argsCount >= 1) {
			return extractIGetFieldInfoFromArg(insn.getArg(0))
		}
		if (insn is InvokeNode && insn.argsCount == 1 && insn.callMth.name == "valueOf") {
			return extractIGetFieldInfoFromArg(insn.getArg(0))
		}
		return null
	}

	private fun processArgs(parentCls: ClassNode, wrapInsn: InsnNode): Boolean {
		try {
			var fldName: String? = null
			for ((i, arg) in wrapInsn.arguments.withIndex()) {
				val insn = arg.unwrap() ?: return false
				if (i % 2 == 0) {
					if (insn !is ConstStringNode) {
						return false
					}
					var str = insn.string
					if (i == 0) {
						// class and first field name
						val parts = str.split(clsSepRgx)
						if (parts.size < 2) {
							return false
						}
						val clsName = parts[0]
						if (NameMapper.isValidIdentifier(clsName)) {
							// skip if class already manually renamed
							try {
								val info = parentCls.getClassInfo()
								if (info != null && info.hasAlias() && RenameUtils.isClassUserRenamed(parentCls)) {
									// don't override
								} else {
									logger.info("rename class '${parentCls.name}' to '$clsName'")
									parentCls.rename(clsName)
									RenameReasonAttr.forNode(parentCls).append("from toString()")
								}
							} catch (e: Exception) {
								// Log exception and continue (do not rethrow) so errors are not silently suppressed
								logger.log(
									Level.SEVERE,
									"Error during class rename check in ToStringRenamePass for class ${parentCls.name}",
									e
								)
							}
						}
						str = parts[1]
					}
					fldName = str.trim('\'', '=', ',', ' ', ':')
				} else {
					if (insn.type != InsnType.IGET) {
						return false
					}
					if (insn !is IndexInsnNode) {
						return false
					}
					val iget = insn
					val fldInfo = iget.index as? FieldInfo ?: return false
					val fld = parentCls.searchField(fldInfo)
					if (fld != null && fldName != null && NameMapper.isValidIdentifier(fldName)) {
						// skip if field already manually renamed
						try {
							val finfo = fld.getFieldInfo()
							if (finfo != null && finfo.hasAlias() && RenameUtils.isFieldUserRenamed(fld)) {
								// don't override
							} else {
								logger.info("rename field '${fld.name}' to '$fldName'")
								fld.rename(fldName)
								RenameReasonAttr.forNode(fld).append("from toString()")
							}
						} catch (e: Exception) {
							// Log exception and continue (do not rethrow) so errors are not silently suppressed
							logger.log(
								Level.SEVERE,
								"Error during field rename in ToStringRenamePass for field ${fld.name} in class ${parentCls.name}",
								e
							)
						}
					}
				}
			}
			return true
		} catch (e: Exception) {
			logger.severe("$e: Args process failed")
			return false
		}
	}
}
