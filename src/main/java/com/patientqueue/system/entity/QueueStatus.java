package com.patientqueue.system.entity;

public enum QueueStatus {
    WAITING{
        @Override
        public boolean canTransitionTo(QueueStatus next){
            return next == IN_CONSULTATION || next == NO_SHOW;
        }
    },
    IN_CONSULTATION{
        @Override
        public boolean canTransitionTo(QueueStatus next) {
            return next == COMPLETED;
        }
    },
    COMPLETED{
        @Override
        public boolean canTransitionTo(QueueStatus next) {
            return false;
        }
    },
    NO_SHOW{
        @Override
        public boolean canTransitionTo(QueueStatus next) {
            return false;
        }
    };

    public abstract boolean canTransitionTo(QueueStatus next);
}
