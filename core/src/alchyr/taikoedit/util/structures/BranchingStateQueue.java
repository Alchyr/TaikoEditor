package alchyr.taikoedit.util.structures;

import java.util.*;

//Node structure with "movement" support,
//Undo/redo will call relevant methods on this object and send messages requesting similar.
//temporary changes will go directly to this object.
public class BranchingStateQueue<T extends BranchingStateQueue.StateChange> {
    private StateNode<T> current;
    private final Map<Integer, StateNode<T>> changeMap = new HashMap<>();
    private int changeKey = Integer.MIN_VALUE;

    public BranchingStateQueue() {
        clear();
    }

    //addChange assumes the change has already occurred.
    //only undo and redo methods will actually *make* changes within this class.
    public int addChange(T change) {
        int stateKey = currentKey();
        current = current.addChange(change, ++changeKey);
        return stateKey;
    }

    public boolean canUndo() {
        return current.change != null;
    }

    public T undo() {
        T change = current.change;

        if (change != null) { //the root node will never have a change
            change.undo();
            current = current.parent;
        }

        return change;
    }

    public boolean canRedo() {
        return current.nextState() != null;
    }

    public T redo() {
        StateNode<T> nextState = current.nextState();
        if (nextState != null) {
            current = nextState;
            current.change.perform();
            return current.change;
        }
        return null;
    }

    public int currentDepth() {
        return current.depth;
    }

    public int currentKey() {
        return current.key;
    }

    public T current() {
        return current.change;
    }

    public void clear() {
        changeKey = Integer.MIN_VALUE;
        changeMap.clear();
        current = new StateNode<>(null, changeKey);
        changeMap.put(Integer.MIN_VALUE, current);
    }

    /**
     * Jump to a target state based on they state's key.
     * @param targetKey
     * @param changeBranch Whether this is allowed to change branches. If changing branches is required and this value is false, null will be returned.
     * @param clearChanges Whether to remove assigned keys to changes that are undone and remove them from the state tree.
     * @return A list of changes that were undone. If moving to an old branch and different changes were redone, this list starts with a null entry. If failed, null.
     */
    public List<T> changeState(int targetKey, boolean changeBranch, boolean clearChanges) {
        int newKey = changeKey;
        List<T> undoneChanges = new ArrayList<>(4);
        List<StateNode<T>> doChanges = new ArrayList<>(4);

        StateNode<T> target = changeMap.get(targetKey), parent = target;
        if (target == null) return null;

        //If moving towards root of tree, eventually they should converge at same depth.
        //If they do not, redo changes and return null.

        while (parent != current) {
            if (parent.depth > currentDepth()) {
                parent = parent.parent;
                if (parent == null) {
                    for (int i = 0; i < undoneChanges.size(); ++i) redo();
                    return null;
                }
                doChanges.add(0, parent);
            }
            else {
                T undone = undo();
                if (undone == null) {
                    for (int i = 0; i < undoneChanges.size(); ++i) redo();
                    return null;
                }
                undoneChanges.add(0, undone);
            }
        }

        if (!doChanges.isEmpty()) {
            boolean changedBranch = false;

            for (int i = 0; i < doChanges.size() - 1; ++i) {
                //doChanges.get(i) should be current
                StateNode<T> targetState = doChanges.get(i + 1);

                if (current.nextState() != targetState) {
                    if (!changedBranch) {
                        changedBranch = true;
                        if (!changeBranch) { //Not allowed to change branches
                            for (int x = 0; x < undoneChanges.size(); ++x) redo();
                            return null;
                        }
                    }
                    current.changeBranch(doChanges.get(i + 1));
                }

                redo();
            }

            if (changedBranch) {
                undoneChanges.add(0, null); //denotes that these changes are considered invalid
            }
        }

        //success
        if (clearChanges && !undoneChanges.isEmpty()) {
            StateNode<T> dropped = parent.removeChange(undoneChanges.get(0) == null ? undoneChanges.get(1) : undoneChanges.get(0));
            if (dropped != null) {
                newKey -= dropped.size();
                changeKey = newKey;
            }

        }

        return undoneChanges;
    }


    private static class StateNode<U extends StateChange> {
        final U change; //the change that *results* in this state.
        int depth;
        final int key;

        StateNode<U> parent = null;

        List<StateNode<U>> followingStates = new ArrayList<>();
        //highest index is the current "next" state


        StateNode(U change, int key) {
            this.change = change;
            this.key = key;
            this.depth = 0;
        }

        StateNode<U> nextState() {
            if (followingStates.isEmpty()) return null;
            return followingStates.get(followingStates.size() - 1);
        }

        StateNode<U> addChange(U change, int key) {
            StateNode<U> nextState = new StateNode<>(change, key);
            followingStates.add(nextState);
            nextState.parent = this;
            nextState.depth = depth + 1;

            return nextState;
        }

        void changeBranch(StateNode<U> targetBranch) {
            if (followingStates.remove(targetBranch)) {
                followingStates.add(targetBranch);
            }
        }

        public StateNode<U> removeChange(U t) {
            Iterator<StateNode<U>> followingStateIterator = followingStates.iterator();
            while (followingStateIterator.hasNext()) {
                StateNode<U> followingState = followingStateIterator.next();
                if (followingState.change == t) {
                    followingStateIterator.remove();
                    return followingState;
                }
            }

            return null;
        }

        public int size() {
            int size = 1;
            for (StateNode<U> child : followingStates) {
                size += child.size();
            }
            return size;
        }
    }

    public static abstract class StateChange {
        public abstract void undo();
        public abstract void perform();
    }
}
