// vars/jsonTreeToSerializable.groovy
import org.customerxp.cd.CdJsonTrees

/**
 * JsonSlurper returns {@code LazyMap} / non-serializable structures; Jenkins CPS cannot persist them.
 * Implementation: {@link CdJsonTrees#toSerializablePlain}.
 *
 * <p>Do not recurse via {@code call(...)} — in Jenkins CPS, {@code call} is the pipeline step entry
 * and repeated {@code call(v)} is treated as nested step dispatch (stack blow-up ~1025).</p>
 */
def call(Object o) {
    return CdJsonTrees.toSerializablePlain(o)
}

/** Internal recursion only (never name this {@code call}). */
def toPlain(Object o) {
    return CdJsonTrees.toSerializablePlain(o)
}
