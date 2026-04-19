package jadx.plugins.renamer.passes

import jadx.api.data.CommentStyle
import jadx.api.plugins.input.data.annotations.EncodedType
import jadx.api.plugins.input.data.annotations.EncodedValue
import jadx.api.plugins.input.data.attributes.JadxAttrType
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr
import jadx.api.plugins.pass.JadxPassInfo
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo
import jadx.api.plugins.pass.types.JadxPreparePass
import jadx.core.dex.nodes.ClassNode
import jadx.core.dex.nodes.RootNode
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.fieldSignature
import kotlinx.metadata.jvm.signature

class KotlinMetadataCommentPass : JadxPreparePass {
	override fun getInfo(): JadxPassInfo {
		return OrderedJadxPassInfo(
			"KotlinMetadataComment",
			"Attach Kotlin @Metadata summary as class comments"
		).before("RenameVisitor")
	}

	override fun init(root: RootNode) {
		for (cls in root.classes) {
			addMetadataCommentIfPresent(cls)
		}
	}

	private fun addMetadataCommentIfPresent(cls: ClassNode) {
		val annotations: AnnotationsAttr = cls.get(JadxAttrType.ANNOTATION_LIST) ?: return
		val metadataAnn = annotations.get(KOTLIN_METADATA_ANNOTATION) ?: return
		val comment = buildMetadataComment(metadataAnn.values)
		if (comment.isNotBlank()) {
			cls.addCodeComment(comment, CommentStyle.BLOCK)
		}
	}

	private fun buildMetadataComment(values: Map<String, EncodedValue>): String {
		val k = values["k"]?.let(::asInt)
		val mv = values["mv"]?.let(::asIntArray)
		val bv = values["bv"]?.let(::asIntArray)
		val d1 = values["d1"]?.let(::asStringArray)
		val d2 = values["d2"]?.let(::asStringArray)
		val xs = values["xs"]?.let(::asString)
		val pn = values["pn"]?.let(::asString)
		val xi = values["xi"]?.let(::asInt)
		return try {
			val metadataAnnotation = buildMetadataAnnotation(k, mv, bv, d1, d2, xs, pn, xi)
			metadataAnnotation.toStringBlock()
		} catch (_: NoClassDefFoundError) {
			buildFallbackComment(k, mv, bv, d1, d2, xs, pn, xi)
		} catch (_: Throwable) {
			buildFallbackComment(k, mv, bv, d1, d2, xs, pn, xi)
		}
	}

	private fun buildFallbackComment(
		k: Int?,
		mv: IntArray?,
		bv: IntArray?,
		d1: List<String>?,
		d2: List<String>?,
		xs: String?,
		pn: String?,
		xi: Int?,
	): String {
		val lines = ArrayList<String>()
		lines.add(COMMENT_PREFIX)
		if (k != null) {
			lines.add("Type: ${kindToText(k)}")
			lines.add("Kind (k): $k")
		}
		if (mv != null) {
			lines.add("Metadata Version (mv): ${mv.joinToString(",")}")
		}
		if (bv != null) {
			lines.add("Bytecode Version (bv): ${bv.joinToString(",")}")
		}
		if (!pn.isNullOrBlank()) {
			lines.add("Package Name (pn): $pn")
		}
		if (!xs.isNullOrBlank()) {
			lines.add("Extra String (xs): $xs")
		}
		if (xi != null) {
			lines.add("Extra Int (xi): $xi")
		}
		if (d1 != null) {
			lines.add("Data1 entries (d1): ${d1.size}")
		}
		if (d2 != null) {
			lines.add("Data2 entries (d2): ${d2.size}")
		}
		return lines.joinToString("\n")
	}

	private fun buildMetadataAnnotation(
		k: Int?,
		mv: IntArray?,
		bv: IntArray?,
		d1: List<String>?,
		d2: List<String>?,
		xs: String?,
		pn: String?,
		xi: Int?,
	): Metadata {
		return Metadata(
			k ?: 1,
			mv ?: intArrayOf(),
			bv ?: intArrayOf(),
			d1?.toTypedArray() ?: emptyArray(),
			d2?.toTypedArray() ?: emptyArray(),
			xs.orEmpty(),
			pn.orEmpty(),
			xi ?: 0,
		)
	}

	private fun asInt(value: EncodedValue): Int? {
		if (value.type != EncodedType.ENCODED_INT) {
			return null
		}
		return value.value as? Int
	}

	private fun asString(value: EncodedValue): String? {
		if (value.type != EncodedType.ENCODED_STRING) {
			return null
		}
		return value.value as? String
	}

	private fun asIntArray(value: EncodedValue): IntArray? {
		if (value.type != EncodedType.ENCODED_ARRAY) {
			return null
		}
		@Suppress("UNCHECKED_CAST")
		val arr = value.value as? List<EncodedValue> ?: return null
		return arr.mapNotNull { item -> asInt(item) }.toIntArray()
	}

	private fun asStringArray(value: EncodedValue): List<String>? {
		if (value.type != EncodedType.ENCODED_ARRAY) {
			return null
		}
		@Suppress("UNCHECKED_CAST")
		val arr = value.value as? List<EncodedValue> ?: return null
		return arr.mapNotNull { item -> asString(item) }
	}

	private fun kindToText(k: Int): String {
		return when (k) {
			1 -> "Class"
			2 -> "File Facade"
			3 -> "Synthetic Class"
			4 -> "Multi-File Class Facade"
			5 -> "Multi-File Class Part"
			else -> "Unknown"
		}
	}

	private fun Metadata.toStringBlock(): String {
		return when (val metadata = KotlinClassMetadata.readLenient(this)) {
			is KotlinClassMetadata.Class -> {
				val klass = metadata.kmClass
				"""$COMMENT_PREFIX
					|Type: Class
					|Class Info:
					|    Name: ${klass.name}
					|    Supertypes: ${klass.supertypes.joinToString(", ") { it.classifier.toString() }}
					|    Type Aliases: ${klass.typeAliases.joinToString(", ") { it.name }}
					|    Companion Object: ${klass.companionObject ?: ""}
					|    Nested Classes: ${klass.nestedClasses.joinToString(", ")}
					|    Enum Entries: ${klass.enumEntries.joinToString(", ")}
					|
					|Constructors:${klass.constructors.joinToString(separator = INDENT, prefix = INDENT) { "${it.signature}, Arguments: ${it.valueParameters.joinToString(", ") { arg -> arg.name ?: "" }}" }}
					|
					|Functions:${klass.functions.joinToString(separator = INDENT, prefix = INDENT) { "${it.signature}, Arguments: ${it.valueParameters.joinToString(", ") { arg -> arg.name ?: "" }}" }}
					|
					|Properties:${klass.properties.joinToString(separator = INDENT, prefix = INDENT) { "${it.fieldSignature}" }}
				""".trimMargin()
			}

			is KotlinClassMetadata.FileFacade -> {
				val klass = metadata.kmPackage
				"""$COMMENT_PREFIX
					|Type: File Facade
					|
					|Functions:${klass.functions.joinToString(separator = INDENT, prefix = INDENT) { "${it.signature}, Arguments: ${it.valueParameters.joinToString(", ") { arg -> arg.name ?: "" }}" }}
					|
					|Properties:${klass.properties.joinToString(separator = INDENT, prefix = INDENT) { "${it.fieldSignature}" }}
				""".trimMargin()
			}

			is KotlinClassMetadata.SyntheticClass -> {
				if (metadata.isLambda) {
					val klass = metadata.kmLambda
					"""$COMMENT_PREFIX
						|Type: Synthetic Class
						|Is Kotlin Lambda: True
						|
						|Functions:
						|    ${klass?.function?.signature}, Arguments: ${klass?.function?.valueParameters?.joinToString(", ") { it.name ?: "" }}
					""".trimMargin()
				} else {
					"""$COMMENT_PREFIX
						|Type: Synthetic Class
						|Is Kotlin Lambda: False
					""".trimMargin()
				}
			}

			is KotlinClassMetadata.MultiFileClassFacade -> {
				"""$COMMENT_PREFIX
					|Type: Multi-File Class Facade
					|This multi-file class combines:
					|${metadata.partClassNames.joinToString(separator = INDENT, prefix = INDENT) { "Class: $it" }}
				""".trimMargin()
			}

			is KotlinClassMetadata.MultiFileClassPart -> {
				val klass = metadata.kmPackage
				"""$COMMENT_PREFIX
					|Type: Multi-File Class Part
					|Name: ${metadata.facadeClassName}
					|
					|Functions:${klass.functions.joinToString(separator = INDENT, prefix = INDENT) { "${it.signature}, Arguments: ${it.valueParameters.joinToString(", ") { arg -> arg.name ?: "" }}" }}
					|
					|Properties:${klass.properties.joinToString(separator = INDENT, prefix = INDENT) { "${it.fieldSignature}" }}
				""".trimMargin()
			}

			is KotlinClassMetadata.Unknown -> "$COMMENT_PREFIX Type: Unknown"
		}
	}

	companion object {
		private const val KOTLIN_METADATA_ANNOTATION = "Lkotlin/Metadata;"
		private const val COMMENT_PREFIX = "Kotlin metadata:"
		private const val INDENT = "\n|    "
	}
}

