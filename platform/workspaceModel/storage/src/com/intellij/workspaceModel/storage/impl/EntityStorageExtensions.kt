// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.workspaceModel.storage.WorkspaceEntity

// ------------------------- Updating references ------------------------

internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                            parentId: EntityId,
                                                                                                            children: Sequence<Child>) {
  if (!connectionId.isParentNullable) {
    val existingChildren = extractOneToManyChildrenIds(connectionId, parentId).toHashSet()
    children.forEach {
      existingChildren.remove(it.id)
    }
    existingChildren.forEach { removeEntity(it) }
  }
  refs.updateOneToManyChildrenOfParent(connectionId, parentId.arrayId, children)
}


internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractManyChildrenOfParent(connectionId: ConnectionId,
                                                                                                                     parentId: ParentEntityId,
                                                                                                                     children: Sequence<Child>) {
  refs.updateOneToAbstractManyChildrenOfParent(connectionId, parentId, children)
}

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractOneParentOfChild(connectionId: ConnectionId,
                                                                                                             childId: ChildEntityId,
                                                                                                             parent: Parent) {
  refs.updateOneToAbstractOneParentOfChild(connectionId, childId, parent)
}

internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToAbstractOneChildOfParent(connectionId: ConnectionId,
                                                                                                                 parentId: ParentEntityId,
                                                                                                                 child: Child?) {
  if (child != null) {
    refs.updateOneToAbstractOneChildOfParent(connectionId, parentId, child)
  }
  else {
    refs.removeOneToAbstractOneRefByParent(connectionId, parentId)
  }
}

internal fun <Child : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneChildOfParent(connectionId: ConnectionId,
                                                                                                        parentId: EntityId,
                                                                                                        child: Child?) {
  if (child != null) {
    refs.updateOneToOneChildOfParent(connectionId, parentId.arrayId, child)
  }
  else {
    refs.removeOneToOneRefByParent(connectionId, parentId.arrayId)
  }
}

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToManyParentOfChild(connectionId: ConnectionId,
                                                                                                      childId: EntityId,
                                                                                                      parent: Parent?) {
  if (parent != null) {
    refs.updateOneToManyParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToManyRefsByChild(connectionId, childId.arrayId)
  }
}

internal fun <Parent : WorkspaceEntityBase> WorkspaceEntityStorageBuilderImpl.updateOneToOneParentOfChild(connectionId: ConnectionId,
                                                                                                     childId: EntityId,
                                                                                                     parent: Parent?) {
  if (!connectionId.isParentNullable && parent != null) {
    // A very important thing. If we replace a field in one-to-one connection, the previous entity is automatically removed.
    val existingChild = extractOneToOneChild<WorkspaceEntityBase>(connectionId, parent.id)
    if (existingChild != null) {
      removeEntity(existingChild)
    }
  }
  if (parent != null) {
    refs.updateOneToOneParentOfChild(connectionId, childId.arrayId, parent)
  }
  else {
    refs.removeOneToOneRefByChild(connectionId, childId.arrayId)
  }
}

// ------------------------- Extracting references references ------------------------

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToManyChildren(connectionId: ConnectionId,
                                                                                      parentId: EntityId): Sequence<Child> {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return emptySequence()
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map {
    val entityData = entitiesList[it]
    if (entityData == null) {
      if (!brokenConsistency) {
        thisLogger().error(
          """Cannot resolve entity.
          |Connection id: $connectionId
          |Unresolved array id: $it
          |All child array ids: ${refs.getOneToManyChildren(connectionId, parentId.arrayId)?.toArray()}
        """.trimMargin()
        )
      }
      null
    } else entityData.createEntity(this)
  }?.filterNotNull() as? Sequence<Child> ?: emptySequence()
}

internal fun AbstractEntityStorage.extractOneToManyChildrenIds(connectionId: ConnectionId, parentId: EntityId): Sequence<EntityId> {
  return refs.getOneToManyChildren(connectionId, parentId.arrayId)?.map { createEntityId(it, connectionId.childClass) } ?: emptySequence()
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractManyChildren(connectionId: ConnectionId,
                                                                                              parentId: ParentEntityId): Sequence<Child> {
  return refs.getOneToAbstractManyChildren(connectionId, parentId)?.asSequence()?.map { pid ->
    entityDataByIdOrDie(pid.id).createEntity(this)
  } as? Sequence<Child> ?: emptySequence()
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractAbstractOneToOneChild(connectionId: ConnectionId,
                                                                                          parentId: ParentEntityId): Child? {
  return refs.getAbstractOneToOneChildren(connectionId, parentId)?.let { entityDataByIdOrDie(it.id).createEntity(this) as Child }
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToAbstractOneParent(connectionId: ConnectionId,
                                                                                            childId: ChildEntityId): Parent? {
  return refs.getOneToAbstractOneParent(connectionId, childId)?.let { entityDataByIdOrDie(it.id).createEntity(this) as Parent }
}

@Suppress("UNCHECKED_CAST")
internal fun <Child : WorkspaceEntity> AbstractEntityStorage.extractOneToOneChild(connectionId: ConnectionId, parentId: EntityId): Child? {
  val entitiesList = entitiesByType[connectionId.childClass] ?: return null
  return refs.getOneToOneChild(connectionId, parentId.arrayId) {
    val childEntityData = entitiesList[it]
    if (childEntityData == null) {
      if (!brokenConsistency) {
        logger<AbstractEntityStorage>().error("""
          Consistency issue. Cannot get a child in one to one connection.
          Connection id: $connectionId
          Parent id: $parentId
          Child array id: $it
        """.trimIndent())
      }
      null
    }
    else childEntityData.createEntity(this) as Child
  }
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToOneParent(connectionId: ConnectionId,
                                                                                    childId: EntityId): Parent? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToOneParent(connectionId, childId.arrayId) {
    val parentEntityData = entitiesList[it]
    if (parentEntityData == null) {
      if (!brokenConsistency) {
        logger<AbstractEntityStorage>().error("""
          Consistency issue. Cannot get a parent in one to one connection.
          Connection id: $connectionId
          Child id: $childId
          Parent array id: $it
        """.trimIndent())
      }
      null
    }
    else parentEntityData.createEntity(this) as Parent
  }
}

@Suppress("UNCHECKED_CAST")
internal fun <Parent : WorkspaceEntity> AbstractEntityStorage.extractOneToManyParent(connectionId: ConnectionId, childId: EntityId): Parent? {
  val entitiesList = entitiesByType[connectionId.parentClass] ?: return null
  return refs.getOneToManyParent(connectionId, childId.arrayId) {
    val parentEntityData = entitiesList[it]
    if (parentEntityData == null) {
      if (!brokenConsistency) {
        logger<AbstractEntityStorage>().error("""
          Consistency issue. Cannot get a parent in one to many connection.
          Connection id: $connectionId
          Child id: $childId
          Parent array id: $it
        """.trimIndent())
      }
      null
    }
    else parentEntityData.createEntity(this) as Parent
  }
}

